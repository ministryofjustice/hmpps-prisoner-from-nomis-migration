package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.CourtEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncCourtEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.tryFetchParent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.DirectionCode.OUT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtMovementRetryMappingMessageTypes.RETRY_MAPPING_COURT_SCHEDULE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtScheduleOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.util.*

private const val TELEMETRY_PREFIX: String = "${CRT_TELEMETRY_PREFIX}-schedule"

@Service
class CourtSchedulerSyncScheduleService(
  override val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
  private val mappingApi: CourtSchedulerMappingApiService,
  private val sentencingMappingApi: CourtSentencingMappingApiService,
  private val nomisApi: CourtSchedulerNomisApiService,
  private val dpsApi: CourtSchedulerDpsApiService,
) : TelemetryEnabled {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun courtScheduleInserted(event: CourtScheduleEvent) = when (event.directionCode) {
    OUT -> syncCourtScheduleOutInserted(event)
    else -> {}
  }

  suspend fun syncCourtScheduleOutInserted(event: CourtScheduleEvent) {
    val (eventId, bookingId, prisonerNumber, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisEventId" to eventId,
      "directionCode" to directionCode,
    )

    if (event.auditExactMatchOrHasMissingAudit(COURT_SCHEDULER_SYNC_AUDIT_MODULE)) {
      telemetryClient.trackEvent("${TELEMETRY_PREFIX}-inserted-ignored", telemetry)
      return
    }

    mappingApi.getCourtScheduleMappingOrNull(eventId)
      ?.also { telemetryClient.trackEvent("${TELEMETRY_PREFIX}-inserted-ignored", telemetry) }
      ?: run {
        track("${TELEMETRY_PREFIX}-inserted", telemetry) {
          syncScheduleOut(prisonerNumber, eventId, telemetry)
            ?.also { tryToCreateScheduleMapping(it, telemetry) }
        }
      }
  }

  suspend fun syncCourtScheduleOut(event: SynchroniseCourtScheduleOutEvent) {
    val (eventId, bookingId, prisonerNumber) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisEventId" to eventId,
      "directionCode" to "OUT",
      "source" to "FROM_NOMIS_SYNC",
    )

    track("${TELEMETRY_PREFIX}-synchronised", telemetry) {
      val existingMapping = mappingApi.getCourtScheduleMappingOrNull(eventId)
        ?.also { telemetry["dpsCourtAppearanceId"] = it.dpsCourtAppearanceId }

      syncScheduleOut(prisonerNumber, eventId, telemetry, existingMapping)
        ?.also { tryToCreateScheduleMapping(it, telemetry) }
    }
  }

  suspend fun syncScheduleOut(
    prisonerNumber: String,
    eventId: Long,
    telemetry: MutableMap<String, Any>,
    existingMapping: CourtScheduleMappingDto? = null,
  ): CourtScheduleMappingDto? = nomisApi.getCourtScheduleOut(prisonerNumber, eventId)
    .let { nomisSchedule ->
      val dpsSentencingId = if (nomisSchedule.courtCaseId != null) {
        tryFetchParent { sentencingMappingApi.getCourtAppearanceOrNullByNomisId(eventId)?.dpsCourtAppearanceId }
      } else {
        null
      }
      telemetry["dpsSentencingCourtAppearanceId"] = "$dpsSentencingId"

      val dpsCourtAppearanceId = dpsApi.syncCourtEvent(prisonerNumber, nomisSchedule.toDpsRequest(existingMapping?.dpsCourtAppearanceId, dpsSentencingId)).id
        .also { telemetry["dpsCourtAppearanceId"] = it }

      CourtScheduleMappingDto(
        prisonerNumber = prisonerNumber,
        bookingId = nomisSchedule.bookingId,
        nomisEventId = eventId,
        dpsCourtAppearanceId = dpsCourtAppearanceId,
        mappingType = CourtScheduleMappingDto.MappingType.NOMIS_CREATED,
      )
    }

  suspend fun courtScheduleUpdated(event: CourtScheduleEvent) = when (event.directionCode) {
    OUT -> syncCourtScheduleOutUpdated(event)
    else -> {}
  }

  suspend fun syncCourtScheduleOutUpdated(event: CourtScheduleEvent) {
    val (eventId, bookingId, prisonerNumber, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisEventId" to eventId,
      "directionCode" to directionCode,
    )

    if (event.auditExactMatchOrHasMissingAudit(COURT_SCHEDULER_SYNC_AUDIT_MODULE)) {
      telemetryClient.trackEvent("${TELEMETRY_PREFIX}-updated-ignored", telemetry)
      return
    }

    track("${TELEMETRY_PREFIX}-updated", telemetry) {
      val existingMapping = mappingApi.getCourtScheduleMappingOrNull(eventId)
        ?.also { telemetry["dpsCourtAppearanceId"] = it.dpsCourtAppearanceId }
        ?: throw IllegalStateException("No mapping found when handling an update event for court schedule $eventId - hopefully messages are being processed out of order and this event will succeed on a retry once the create event is processed. Otherwise we need to understand why the original create event was never processed.")

      syncScheduleOut(prisonerNumber, eventId, telemetry, existingMapping)
    }
  }

  suspend fun courtScheduleDeleted(event: CourtScheduleEvent) = when (event.directionCode) {
    OUT -> syncCourtScheduleOutDeleted(event)
    else -> {}
  }

  suspend fun syncCourtScheduleOutDeleted(event: CourtScheduleEvent) {
    val (eventId, bookingId, prisonerNumber, _, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisEventId" to eventId,
      "directionCode" to directionCode,
    )
    mappingApi.getCourtScheduleMappingOrNull(eventId)?.also {
      track("${TELEMETRY_PREFIX}-deleted", telemetry) {
        telemetry["dpsCourtAppearanceId"] = it.dpsCourtAppearanceId
        dpsApi.deleteCourtEvent(it.dpsCourtAppearanceId)
        mappingApi.deleteCourtScheduleMapping(eventId)
      }
    } ?: run { telemetryClient.trackEvent("${TELEMETRY_PREFIX}-deleted-ignored", telemetry) }
  }

  private suspend fun tryToCreateScheduleMapping(mapping: CourtScheduleMappingDto, telemetry: MutableMap<String, Any>) {
    try {
      mappingApi.createCourtScheduleMapping(mapping)
        .takeIf { it.isError }
        ?.also {
          with(it.errorResponse!!.moreInfo) {
            telemetryClient.trackEvent(
              "${TELEMETRY_PREFIX}-inserted-duplicate",
              mapOf(
                "existingOffenderNo" to existing!!.prisonerNumber,
                "existingBookingId" to existing.bookingId,
                "existingNomisEventId" to existing.nomisEventId,
                "existingDpsCourtAppearanceId" to existing.dpsCourtAppearanceId,
                "duplicateOffenderNo" to duplicate.prisonerNumber,
                "duplicateBookingId" to duplicate.bookingId,
                "duplicateNomisEventId" to duplicate.nomisEventId,
                "duplicateDpsCourtAppearanceId" to duplicate.dpsCourtAppearanceId,
              ),
            )
          }
        }
    } catch (e: Exception) {
      log.error("Failed to create mapping for court schedule with NOMIS id ${mapping.nomisEventId}", e)
      queueService.sendMessage(
        messageType = RETRY_MAPPING_COURT_SCHEDULE.name,
        synchronisationType = SynchronisationType.COURT_SCHEDULER,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryCreateScheduleMapping(retryMessage: InternalMessage<CourtScheduleMappingDto>) {
    mappingApi.createCourtScheduleMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "${TELEMETRY_PREFIX}-mapping-retry-created",
        retryMessage.telemetryAttributes,
      )
    }
  }
}

private fun CourtScheduleOut.toDpsRequest(courtAppearanceId: UUID?, sentencingCourtAppearanceId: String?) = SyncCourtEvent(
  courtEvent = CourtEvent(
    prisonCodeAtTimeOfScheduling = this.prison,
    agyLocId = court,
    start = startTime,
    courtEventType = eventType,
    eventStatus = eventStatus,
    dpsId = courtAppearanceId,
    eventId = eventId,
    commentText = comment,
    // TODO SDIT-3845 Replace with value returned from NOMIS CourtScheduleOut when available
    currentTerm = true,
    externalReferenceUrn = sentencingCourtAppearanceId?.let { "$EXTERNAL_REF_PREFIX$sentencingCourtAppearanceId" },
  ),
  occurredAt = this.audit.modifyDatetime ?: this.audit.createDatetime,
  user = SyncUser(
    username = audit.modifyUserId ?: audit.createUsername,
    activeCaseloadId = userActiveCaseloadId,
  ),
)
