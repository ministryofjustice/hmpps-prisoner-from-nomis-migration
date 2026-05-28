package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.CourtEventMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncCourtEventMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.tryFetchParent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.DirectionCode.IN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.DirectionCode.OUT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.MovementType.CRT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtMovementRetryMappingMessageTypes.RETRY_MAPPING_COURT_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapMovementService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtMovementIn
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtMovementOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.util.*

private const val TELEMETRY_PREFIX: String = "${CRT_TELEMETRY_PREFIX}-movement"

@Service
class CourtSchedulerSyncMovementService(
  override val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
  private val mappingApi: CourtSchedulerMappingApiService,
  private val nomisApi: CourtSchedulerNomisApiService,
  private val dpsApi: CourtSchedulerDpsApiService,
) : TelemetryEnabled {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun courtMovementChanged(event: ExternalMovementEvent) = when {
    event.movementType != CRT -> {}
    event.recordInserted -> courtMovementInserted(event)
    event.recordDeleted -> courtMovementDeleted(event)
    else -> courtMovementUpdated(event)
  }

  suspend fun courtMovementInserted(event: ExternalMovementEvent) {
    val (bookingId, prisonerNumber, movementSeq, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber!!,
      "bookingId" to bookingId,
      "movementSeq" to movementSeq,
      "directionCode" to directionCode,
    )

    if (event.originatesInDps) {
      telemetryClient.trackEvent("${TELEMETRY_PREFIX}-inserted-skipped", telemetry)
      return
    }

    mappingApi.getCourtMovementMappingOrNull(bookingId, movementSeq)
      ?.also {
        telemetry["dpsCourtMovementId"] = it.dpsCourtMovementId
        telemetryClient.trackEvent("${TELEMETRY_PREFIX}-inserted-ignored", telemetry)
      }
      ?: run {
        track("${TELEMETRY_PREFIX}-inserted", telemetry) {
          val mapping = when (directionCode) {
            OUT -> courtMovementOutRequest(prisonerNumber, bookingId, movementSeq, telemetry)
            IN -> courtMovementInRequest(prisonerNumber, bookingId, movementSeq, telemetry)
          }.let { dpsRequest ->
            val dpsCourtMovementId = dpsApi.syncCourtMovement(prisonerNumber, dpsRequest).id
              .also { telemetry["dpsCourtMovementId"] = it }

            CourtMovementMappingDto(
              prisonerNumber = prisonerNumber,
              nomisBookingId = bookingId,
              nomisMovementSeq = movementSeq,
              dpsCourtMovementId = dpsCourtMovementId,
              mappingType = CourtMovementMappingDto.MappingType.NOMIS_CREATED,
            )
          }
          tryToCreateCourtMovementMapping(mapping, telemetry)
        }
      }
  }

  private suspend fun courtMovementOutRequest(
    prisonerNumber: String,
    bookingId: Long,
    movementSeq: Int,
    telemetry: MutableMap<String, Any>,
    dpsCourtMovementId: UUID? = null,
  ): SyncCourtEventMovement {
    val nomis = nomisApi.getCourtMovementOut(prisonerNumber, bookingId, movementSeq)
    val dpsCourtAppearanceId = nomis.courtScheduleOutId
      ?.also { telemetry["nomisEventId"] = it }
      ?.let {
        tryFetchParent { mappingApi.getCourtScheduleMappingOrNull(it)?.dpsCourtAppearanceId }
          .also { telemetry["dpsCourtAppearanceId"] = it }
      }

    return nomis.toDpsRequest(dpsCourtAppearanceScheduleId = dpsCourtAppearanceId, dpsCourtMovementId = dpsCourtMovementId)
  }

  private suspend fun courtMovementInRequest(
    prisonerNumber: String,
    bookingId: Long,
    movementSeq: Int,
    telemetry: MutableMap<String, Any>,
    dpsCourtMovementId: UUID? = null,
  ): SyncCourtEventMovement {
    val nomis = nomisApi.getCourtMovementIn(prisonerNumber, bookingId, movementSeq)
    val dpsCourtAppearanceId = nomis.courtScheduleOutId
      ?.also { telemetry["nomisEventId"] = it }
      ?.let {
        tryFetchParent { mappingApi.getCourtScheduleMappingOrNull(it)?.dpsCourtAppearanceId }
          .also { telemetry["dpsCourtAppearanceId"] = it }
      }

    return nomis.toDpsRequest(dpsCourtAppearanceScheduleId = dpsCourtAppearanceId, dpsCourtMovementId = dpsCourtMovementId)
  }

  suspend fun courtMovementUpdated(event: ExternalMovementEvent) {
    val (bookingId, prisonerNumber, movementSeq, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber!!,
      "bookingId" to bookingId,
      "movementSeq" to movementSeq,
      "directionCode" to directionCode,
    )

    if (event.originatesInDps) {
      telemetryClient.trackEvent("${TELEMETRY_PREFIX}-updated-skipped", telemetry)
      return
    }

    track("${TELEMETRY_PREFIX}-updated", telemetry) {
      val existingMapping = mappingApi.getCourtMovementMappingOrNull(bookingId, movementSeq)
        ?.also { telemetry["dpsCourtMovementId"] = it.dpsCourtMovementId }
        ?: throw IllegalStateException("No mapping found when handling an update event for movement $bookingId/$movementSeq - hopefully messages are being processed out of order and this event will succeed on a retry once the create event is processed. Otherwise we need to understand why the original create event was never processed.")

      when (directionCode) {
        OUT -> courtMovementOutRequest(prisonerNumber, bookingId, movementSeq, telemetry, existingMapping.dpsCourtMovementId)
        IN -> courtMovementInRequest(prisonerNumber, bookingId, movementSeq, telemetry, existingMapping.dpsCourtMovementId)
      }.let { dpsRequest ->
        dpsApi.syncCourtMovement(prisonerNumber, dpsRequest).id
          .also { telemetry["dpsCourtMovementId"] = it }
      }
    }
  }

  suspend fun courtMovementDeleted(event: ExternalMovementEvent) {
    val (bookingId, prisonerNumber, movementSeq, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber!!,
      "bookingId" to bookingId,
      "movementSeq" to movementSeq,
      "directionCode" to directionCode,
    )
    mappingApi.getCourtMovementMappingOrNull(bookingId, movementSeq)?.also {
      telemetry["dpsCourtMovementId"] = it.dpsCourtMovementId
      track("${TELEMETRY_PREFIX}-deleted", telemetry) {
        dpsApi.deleteCourtMovement(it.dpsCourtMovementId)
        mappingApi.deleteCourtMovementMapping(bookingId, movementSeq)
      }
    } ?: run { telemetryClient.trackEvent("${TELEMETRY_PREFIX}-deleted-ignored", telemetry) }
  }

  private suspend fun tryToCreateCourtMovementMapping(
    mapping: CourtMovementMappingDto,
    telemetry: MutableMap<String, Any>,
  ) {
    try {
      mappingApi.createCourtMovementMapping(mapping).takeIf { it.isError }?.also {
        with(it.errorResponse!!.moreInfo) {
          telemetryClient.trackEvent(
            "${TELEMETRY_PREFIX}-inserted-duplicate",
            mapOf(
              "existingOffenderNo" to existing!!.prisonerNumber,
              "existingBookingId" to existing.nomisBookingId,
              "existingMovementSeq" to existing.nomisMovementSeq,
              "existingDpsCourtMovementId" to existing.dpsCourtMovementId,
              "duplicateOffenderNo" to duplicate.prisonerNumber,
              "duplicateBookingId" to duplicate.nomisBookingId,
              "duplicateMovementSeq" to duplicate.nomisMovementSeq,
              "duplicateDpsCourtMovementId" to duplicate.dpsCourtMovementId,
            ),
          )
        }
      }
    } catch (e: Exception) {
      TapMovementService.log.error(
        "Failed to create mapping for court movement NOMIS id ${mapping.nomisBookingId}/${mapping.nomisMovementSeq}",
        e,
      )
      queueService.sendMessage(
        messageType = RETRY_MAPPING_COURT_MOVEMENT.name,
        synchronisationType = SynchronisationType.COURT_SCHEDULER,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryCreateMovementMapping(retryMessage: InternalMessage<CourtMovementMappingDto>) {
    mappingApi.createCourtMovementMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "${TELEMETRY_PREFIX}-mapping-retry-created",
        retryMessage.telemetryAttributes,
      )
    }
  }
}

private fun CourtMovementOut.toDpsRequest(
  dpsCourtMovementId: UUID? = null,
  dpsCourtAppearanceScheduleId: UUID? = null,
) = SyncCourtEventMovement(
  movement = CourtEventMovement(
    directionCode = "OUT",
    dpsId = dpsCourtMovementId,
    dpsCourtAppearanceScheduleId = dpsCourtAppearanceScheduleId,
    offenderBookId = bookingId,
    movementSeq = sequence,
    occurredAt = movementTime,
    movementReasonCode = this.movementReason,
    fromAgencyId = fromPrison,
    toAgencyId = toCourt ?: MISSING_COURT,
    commentText = this.commentText,
  ),
  occurredAt = this.audit.modifyDatetime ?: this.audit.createDatetime,
  user = SyncUser(
    username = audit.modifyUserId ?: audit.createUsername,
    activeCaseloadId = userActiveCaseloadId,
  ),
)

private fun CourtMovementIn.toDpsRequest(
  dpsCourtMovementId: UUID? = null,
  dpsCourtAppearanceScheduleId: UUID? = null,
) = SyncCourtEventMovement(
  movement = CourtEventMovement(
    directionCode = "IN",
    dpsId = dpsCourtMovementId,
    dpsCourtAppearanceScheduleId = dpsCourtAppearanceScheduleId,
    offenderBookId = bookingId,
    movementSeq = sequence,
    occurredAt = movementTime,
    movementReasonCode = this.movementReason,
    fromAgencyId = fromCourt ?: MISSING_COURT,
    toAgencyId = toPrison,
    commentText = this.commentText,
  ),
  occurredAt = this.audit.modifyDatetime ?: this.audit.createDatetime,
  user = SyncUser(
    username = audit.modifyUserId ?: audit.createUsername,
    activeCaseloadId = userActiveCaseloadId,
  ),
)
