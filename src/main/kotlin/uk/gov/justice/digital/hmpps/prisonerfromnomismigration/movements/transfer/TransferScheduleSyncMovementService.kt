package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited.Companion.DPS_SYNC_AUDIT_MODULE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.tryFetchParent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.MovementType.TRN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.toDpsUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransfersRetryMappingMessageTypes.RETRY_MAPPING_TRANSFER_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferMovementMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TransferMovementOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncMovementRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncUser
import java.util.*

private const val TELEMETRY_PREFIX: String = "${TRANSFER_TELEMETRY_PREFIX}-movement"

@Service
class TransferScheduleSyncMovementService(
  override val telemetryClient: TelemetryClient,
  private val mappingApiService: TransferScheduleMappingApiService,
  private val nomisApiService: TransferScheduleNomisApiService,
  private val dpsApiService: TransferScheduleDpsApiService,
  private val queueService: SynchronisationQueueService,
) : TelemetryEnabled {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun transferMovementChanged(event: ExternalMovementEvent) = when {
    event.movementType != TRN -> {}
    // TODO SDIT-4157 Handle edit external movements updates (with a repair)
    event.recordInserted -> transferMovementInserted(event)
    event.recordDeleted -> transferMovementDeleted(event)
    else -> transferMovementUpdated(event)
  }

  suspend fun transferMovementInserted(event: ExternalMovementEvent) {
    val (bookingId, prisonerNumber, movementSeq) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber!!,
      "bookingId" to bookingId,
      "movementSeq" to movementSeq,
    )

    if (event.auditExactMatchOrHasMissingAudit(DPS_SYNC_AUDIT_MODULE)) {
      telemetryClient.trackEvent("${TELEMETRY_PREFIX}-inserted-ignored", telemetry)
      return
    }

    mappingApiService.getTransferMovementMappingOrNull(bookingId, movementSeq)
      ?.also {
        telemetry["dpsTransferMovementId"] = it.dpsTransferMovementId
        telemetryClient.trackEvent("${TELEMETRY_PREFIX}-inserted-ignored", telemetry)
      }
      ?: run {
        track("${TELEMETRY_PREFIX}-inserted", telemetry) {
          syncTransferMovement(prisonerNumber, bookingId, movementSeq, telemetry)
            .also { tryToCreateTransferMovementMapping(it, telemetry) }
        }
      }
  }

  suspend fun transferMovementUpdated(event: ExternalMovementEvent) {
    val (bookingId, prisonerNumber, movementSeq) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber!!,
      "bookingId" to bookingId,
      "movementSeq" to movementSeq,
    )

    if (event.auditExactMatchOrHasMissingAudit(DPS_SYNC_AUDIT_MODULE)) {
      telemetryClient.trackEvent("${TELEMETRY_PREFIX}-updated-ignored", telemetry)
      return
    }

    mappingApiService.getTransferMovementMappingOrNull(bookingId, movementSeq)
      ?. run {
        track("${TELEMETRY_PREFIX}-updated", telemetry) {
          syncTransferMovement(prisonerNumber, bookingId, movementSeq, telemetry, dpsId = dpsTransferMovementId)
        }
      }
      ?: throw IllegalStateException("No mapping found when handling an update event for movement $bookingId/$movementSeq - hopefully messages are being processed out of order and this event will succeed on a retry once the create event is processed. Otherwise we need to understand why the original create event was never processed.")
  }

  suspend fun transferMovementDeleted(event: ExternalMovementEvent) {
    val (bookingId, prisonerNumber, movementSeq) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber!!,
      "bookingId" to bookingId,
      "movementSeq" to movementSeq,
    )
    mappingApiService.getTransferMovementMappingOrNull(bookingId, movementSeq)?.also {
      telemetry["dpsTransferMovementId"] = it.dpsTransferMovementId
      track("${TELEMETRY_PREFIX}-deleted", telemetry) {
        dpsApiService.deleteTransferMovement(it.dpsTransferMovementId)
        mappingApiService.deleteTransferMovementMapping(bookingId, movementSeq)
      }
    } ?: run { telemetryClient.trackEvent("${TELEMETRY_PREFIX}-deleted-ignored", telemetry) }
  }

  private suspend fun syncTransferMovement(
    prisonerNumber: String,
    bookingId: Long,
    movementSeq: Int,
    telemetry: MutableMap<String, Any>,
    dpsId: UUID? = null,
  ): TransferMovementMappingDto {
    val nomis = nomisApiService.getTransferMovementOut(prisonerNumber, bookingId, movementSeq)
      .also { telemetry["nomisEventId"] = "${it.transferScheduleOutId}" }
    val dpsScheduleTransferId = nomis.transferScheduleOutId?.let {
      tryFetchParent { mappingApiService.getTransferScheduleMappingOrNull(it) }
        .also { telemetry["dpsTransferScheduleId"] = it.dpsTransferScheduleId }
    }?.dpsTransferScheduleId
    val dps = dpsApiService.syncTransferMovement(
      prisonerNumber,
      nomis.toDpsRequest(dpsId = dpsId, dpsTransferScheduleId = dpsScheduleTransferId),
    )
      .also { telemetry["dpsTransferMovementId"] = it.dpsId }

    return TransferMovementMappingDto(
      prisonerNumber,
      bookingId,
      movementSeq,
      dps.dpsId,
      NOMIS_CREATED,
    )
  }

  private suspend fun tryToCreateTransferMovementMapping(
    mapping: TransferMovementMappingDto,
    telemetry: MutableMap<String, Any>,
  ) {
    try {
      mappingApiService.createTransferMovementMapping(mapping).takeIf { it.isError }?.also {
        with(it.errorResponse!!.moreInfo) {
          telemetryClient.trackEvent(
            "${TELEMETRY_PREFIX}-inserted-duplicate",
            mapOf(
              "existingOffenderNo" to existing!!.prisonerNumber,
              "existingBookingId" to existing.nomisBookingId,
              "existingMovementSeq" to existing.nomisMovementSeq,
              "existingDpsTransferMovementId" to existing.dpsTransferMovementId,
              "duplicateOffenderNo" to duplicate.prisonerNumber,
              "duplicateBookingId" to duplicate.nomisBookingId,
              "duplicateMovementSeq" to duplicate.nomisMovementSeq,
              "duplicateDpsTransferMovementId" to duplicate.dpsTransferMovementId,
            ),
          )
        }
      }
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for transfer movement NOMIS id ${mapping.nomisBookingId}/${mapping.nomisMovementSeq}",
        e,
      )
      queueService.sendMessage(
        messageType = RETRY_MAPPING_TRANSFER_MOVEMENT.name,
        synchronisationType = SynchronisationType.TRANSFER_SCHEDULER,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryCreateMovementMapping(retryMessage: InternalMessage<TransferMovementMappingDto>) {
    mappingApiService.createTransferMovementMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "${TELEMETRY_PREFIX}-mapping-retry-created",
        retryMessage.telemetryAttributes,
      )
    }
  }
}

private fun TransferMovementOut.toDpsRequest(dpsId: UUID? = null, dpsTransferScheduleId: UUID? = null) = SyncMovementRequest(
  movement = SyncMovement(
    occurredAt = movementTime,
    movementReasonCode = movementReason,
    escortCode = escort ?: DEFAULT_ESCORT_CODE,
    fromAgyLocId = fromPrison,
    toAgyLocId = toPrison,
    dpsId = dpsId,
    dpsTransferId = dpsTransferScheduleId,
    offenderBookId = bookingId,
    movementSeq = sequence,
    active = active,
    commentText = commentText,
  ),
  syncUser = SyncUser(
    username = (audit.modifyDisplayName ?: audit.createUsername).toDpsUser(),
    activeCaseloadId = userActiveCaseloadId,
  ),
  occurredAt = audit.modifyDatetime ?: audit.createDatetime,
)
