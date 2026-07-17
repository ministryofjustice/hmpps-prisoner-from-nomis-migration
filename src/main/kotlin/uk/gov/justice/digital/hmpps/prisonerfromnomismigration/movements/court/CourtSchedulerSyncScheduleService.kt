package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.toDpsUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtScheduleOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.util.*
import kotlin.collections.set

private const val TELEMETRY_PREFIX: String = "${CRT_TELEMETRY_PREFIX}-schedule"

@Service
class CourtSchedulerSyncScheduleService(
  override val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
  private val mappingApi: CourtSchedulerMappingApiService,
  private val sentencingMappingApi: CourtSentencingMappingApiService,
  private val nomisApi: CourtSchedulerNomisApiService,
  private val dpsApi: CourtSchedulerDpsApiService,
  private val nomisSyncApi: CourtSchedulerNomisSyncApiService,
  private val features: CourtSchedulerFeatureSwitches,
) : TelemetryEnabled {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun courtScheduleInserted(event: CourtScheduleEvent) = when (event.directionCode) {
    OUT -> syncCourtScheduleOutInserted(event)
    else -> {}
  }

  suspend fun syncCourtScheduleOutInserted(event: CourtScheduleEvent) {
    val (eventId, bookingId, prisonerNumber, caseId, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisEventId" to eventId,
      "directionCode" to directionCode,
    ).apply {
      if (caseId != null) this["caseId"] = caseId
    }

    if (event.auditExactMatchOrHasMissingAudit(COURT_SCHEDULER_SYNC_AUDIT_MODULE) || (caseId != null && features.ignoreInsertAndUpdateSentencingEvents)) {
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

  suspend fun syncCourtScheduleOut(event: InternalMessage<SynchroniseCourtScheduleOutEvent>) {
    val (eventId, bookingId, prisonerNumber) = event.body
    val telemetry = event.telemetryAttributes.toMutableMap<String, Any>()
      .apply {
        this["offenderNo"] = prisonerNumber
        this["bookingId"] = bookingId
        this["nomisEventId"] = eventId
        this["directionCode"] = "OUT"
        this["source"] = "FROM_NOMIS_SYNC"
      }

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
    val (eventId, bookingId, prisonerNumber, caseId, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisEventId" to eventId,
      "directionCode" to directionCode,
    ).apply {
      if (caseId != null) this["caseId"] = caseId
    }

    if (event.auditExactMatchOrHasMissingAudit(COURT_SCHEDULER_SYNC_AUDIT_MODULE) || (caseId != null && features.ignoreInsertAndUpdateSentencingEvents)) {
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
    val (eventId, bookingId, prisonerNumber, caseId, directionCode) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisEventId" to eventId,
      "directionCode" to directionCode,
    ).apply {
      if (caseId != null) this["caseId"] = caseId
    }

    if (event.auditExactMatchOrHasMissingAudit(COURT_SCHEDULER_SYNC_AUDIT_MODULE) || (caseId != null && features.ignoreDeletedSentencingEvents)) {
      telemetryClient.trackEvent("${TELEMETRY_PREFIX}-deleted-ignored", telemetry)
      return
    }

    fun Exception.trackAndRethrow(): Nothing {
      telemetry["error"] = message ?: "Unknown error"
      telemetryClient.trackEvent("${TELEMETRY_PREFIX}-deleted-error", telemetry)
      throw this
    }

    mappingApi.getCourtScheduleMappingOrNull(eventId)?.also {
      try {
        telemetry["dpsCourtAppearanceId"] = it.dpsCourtAppearanceId
        dpsApi.deleteCourtEvent(it.dpsCourtAppearanceId)
        mappingApi.deleteCourtScheduleMapping(eventId)
      }
      // A conflict from DPS always means we should try to recreate the court schedule
      catch (_: WebClientResponseException.Conflict) {
        try {
          nomisSyncApi.recreateCourtScheduleInNomis(prisonerNumber, it.dpsCourtAppearanceId)
          telemetryClient.trackEvent("${TELEMETRY_PREFIX}-deleted-recreated", telemetry)
          return
        } catch (ex: Exception) {
          ex.trackAndRethrow()
        }
      }
      // Any other error means we failed
      catch (ex: Exception) {
        ex.trackAndRethrow()
      }

      telemetryClient.trackEvent("${TELEMETRY_PREFIX}-deleted-success", telemetry)
    } ?: run { telemetryClient.trackEvent("${TELEMETRY_PREFIX}-deleted-ignored", telemetry) }
  }

  private suspend fun tryToCreateScheduleMapping(mapping: CourtScheduleMappingDto, telemetry: MutableMap<String, Any>) {
    try {
      createScheduleMapping(mapping, telemetry)
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
    createScheduleMapping(retryMessage.body, retryMessage.telemetryAttributes.toMutableMap())
      .also {
        telemetryClient.trackEvent(
          "${TELEMETRY_PREFIX}-mapping-retry-created",
          retryMessage.telemetryAttributes,
        )
      }
  }

  private suspend fun createScheduleMapping(mapping: CourtScheduleMappingDto, telemetry: MutableMap<String, Any>) {
    val mappingResponse = mappingApi.upsertCourtScheduleMappingByDpsId(mapping)
    if (!mappingResponse.isError && mappingResponse.successResponse!!.replacedNomisEventId != null) {
      telemetry["replacedNomisEventId"] = mappingResponse.successResponse.replacedNomisEventId
      runCatching {
        queueService.sendMessage(
          messageType = SYNC_COURT_SCHEDULE,
          synchronisationType = SynchronisationType.COURT_SCHEDULER,
          message = SynchroniseCourtScheduleOutEvent(
            eventId = mappingResponse.successResponse.replacedNomisEventId,
            bookingId = mapping.bookingId,
            offenderIdDisplay = mapping.prisonerNumber,
          ),
          telemetryAttributes = mapOf("originalNomisEventId" to telemetry["nomisEventId"].toString()),
        )
      }.onFailure {
        log.error("Failed to send sync message for court schedule event ID=${mappingResponse.successResponse.replacedNomisEventId} for offender ${mapping.prisonerNumber}", it)
      }
    } else if (mappingResponse.isError) {
      with(mappingResponse.errorResponse!!.moreInfo) {
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
    currentTerm = latestBooking,
    externalReferenceUrn = sentencingCourtAppearanceId?.let { "$EXTERNAL_REF_PREFIX$sentencingCourtAppearanceId" },
  ),
  occurredAt = this.audit.modifyDatetime ?: this.audit.createDatetime,
  user = SyncUser(
    username = audit.modifyUserId?.toDpsUser() ?: audit.createUsername.toDpsUser(),
    activeCaseloadId = userActiveCaseloadId,
  ),
)
