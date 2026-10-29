package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited.Companion.DPS_SYNC_AUDIT_MODULE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.tryFetchParent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationRetryMappingMessageTypes.RETRY_MAPPING_PERSON_LOCATION_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.toDpsUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TRN_URN_PREFIX
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransferScheduleMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationMovementIdMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationMovementIdMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonLocationMovement.MovementType.TRN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovementRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.util.UUID

private const val TELEMETRY_PREFIX = "person-location-sync-movement"

@Service
class PersonLocationSyncMovementService(
  override val telemetryClient: TelemetryClient,
  private val nomisApiService: PersonLocationNomisApiService,
  private val mappingApiService: PersonLocationMappingApiService,
  private val dpsApiService: PersonLocationDpsApiService,
  private val transferScheduleMappingApiService: TransferScheduleMappingApiService,
  private val queueService: SynchronisationQueueService,
) : TelemetryEnabled {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun externalMovementChanged(event: ExternalMovementEvent) {
    // Updates and deletes are not synchronised yet
    if (event.recordInserted) {
      externalMovementInserted(event)
    }
  }

  private suspend fun externalMovementInserted(event: ExternalMovementEvent) {
    val (bookingId, prisonerNumber, movementSeq) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber!!,
      "bookingId" to bookingId,
      "movementSeq" to movementSeq,
    )

    if (event.auditExactMatchOrHasMissingAudit(DPS_SYNC_AUDIT_MODULE)) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-inserted-ignored", telemetry)
      return
    }

    mappingApiService.getMovementMappingOrNull(bookingId, movementSeq)?.also {
      telemetry["dpsExternalMovementId"] = it.dpsExternalMovementId
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-inserted-ignored", telemetry)
      return
    }

    track("$TELEMETRY_PREFIX-inserted", telemetry) {
      val nomis = nomisApiService.getPersonLocationMovement(prisonerNumber, bookingId, movementSeq)
        .also { telemetry["nomisEventId"] = "${it.scheduleId}" }
      // Schedule references for other movement types are not synchronised yet
      val dpsScheduleId = nomis.scheduleId?.takeIf { nomis.movementType == TRN }?.let { scheduleId ->
        tryFetchParent { transferScheduleMappingApiService.getTransferScheduleMappingOrNull(scheduleId) }
          .dpsTransferScheduleId
      }
      val dpsCustodialSeriesId = tryFetchParent { mappingApiService.getBookingMappingOrNull(bookingId) }
        .dpsCustodialSeriesId

      val dps = dpsApiService.syncExternalMovement(
        prisonerNumber,
        nomis.toDpsRequest(dpsCustodialSeriesId, dpsScheduleId?.let { "$TRN_URN_PREFIX$it" }),
      ).also { telemetry["dpsExternalMovementId"] = it.dpsId }

      PersonLocationMovementIdMappingDto(
        nomisBookingId = bookingId,
        nomisMovementSeq = movementSeq,
        dpsExternalMovementId = dps.dpsId,
        mappingType = NOMIS_CREATED,
      ).also { tryToCreateMapping(it, telemetry) }
    }
  }

  private suspend fun tryToCreateMapping(mapping: PersonLocationMovementIdMappingDto, telemetry: Map<String, Any>) {
    try {
      mappingApiService.createMovementMapping(mapping).takeIf { it.isError }?.also {
        with(it.errorResponse!!.moreInfo) {
          telemetryClient.trackEvent(
            "$TELEMETRY_PREFIX-inserted-duplicate",
            mapOf(
              "existingBookingId" to existing!!.nomisBookingId,
              "existingMovementSeq" to existing.nomisMovementSeq,
              "existingDpsExternalMovementId" to existing.dpsExternalMovementId,
              "duplicateBookingId" to duplicate.nomisBookingId,
              "duplicateMovementSeq" to duplicate.nomisMovementSeq,
              "duplicateDpsExternalMovementId" to duplicate.dpsExternalMovementId,
            ),
          )
        }
      }
    } catch (e: Exception) {
      log.error("Failed to create mapping for person location movement ${mapping.nomisBookingId}/${mapping.nomisMovementSeq}", e)
      queueService.sendMessage(
        messageType = RETRY_MAPPING_PERSON_LOCATION_MOVEMENT.name,
        synchronisationType = SynchronisationType.PERSON_LOCATIONS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryCreateMovementMapping(retryMessage: InternalMessage<PersonLocationMovementIdMappingDto>) {
    mappingApiService.createMovementMapping(retryMessage.body).also {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-mapping-retry-created", retryMessage.telemetryAttributes)
    }
  }
}

private fun PersonLocationMovement.toDpsRequest(dpsCustodialSeriesId: UUID, scheduleReference: String?) = SyncExternalMovementRequest(
  occurredAt = audit.modifyDatetime ?: audit.createDatetime,
  syncUser = SyncUser(username = (audit.modifyUserId ?: audit.createUsername).toDpsUser()),
  movement = toSyncExternalMovement(dpsId = null, dpsCustodySeriesId = dpsCustodialSeriesId, scheduleReference = scheduleReference),
)
