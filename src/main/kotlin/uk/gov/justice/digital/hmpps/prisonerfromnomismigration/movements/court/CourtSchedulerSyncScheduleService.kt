package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.CourtEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncCourtEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.SyncUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.tryFetchParent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.DirectionCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtMovementRetryMappingMessageTypes.RETRY_MAPPING_COURT_SCHEDULE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtScheduleOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

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
    DirectionCode.OUT -> syncCourtScheduleOutInserted(event)
    // TODO when direction is added to the event put this else back in - for now we'll have to check the direction after the nomis call
    //    else -> log.info("Ignoring insert of scheduled movement event ID ${event.eventId} with direction ${event.directionCode} ")
    else -> syncCourtScheduleOutInserted(event)
  }

  suspend fun syncCourtScheduleOutInserted(event: CourtScheduleEvent) {
    val (eventId, bookingId, prisonerNumber) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisEventId" to eventId,
    )

    mappingApi.getCourtScheduleMappingOrNull(eventId)
      ?.also { telemetryClient.trackEvent("${TELEMETRY_PREFIX}-inserted-ignored", telemetry) }
      ?: run {
        track("${TELEMETRY_PREFIX}-inserted", telemetry) {
          syncScheduleOut(prisonerNumber, eventId, telemetry)
            ?.also { tryToCreateScheduleMapping(it, telemetry) }
        }
      }
  }

  suspend fun syncScheduleOut(
    prisonerNumber: String,
    eventId: Long,
    telemetry: MutableMap<String, Any>,
    existingMapping: TapScheduleMappingDto? = null,
  ): CourtScheduleMappingDto? = nomisApi.getCourtScheduleOut(prisonerNumber, eventId)
    .also { telemetry["directionCode"] = "OUT" }
    .let { nomisSchedule ->
      val dpsSentencingId = if (nomisSchedule.courtCaseId != null) {
        tryFetchParent { sentencingMappingApi.getCourtAppearanceOrNullByNomisId(eventId)?.dpsCourtAppearanceId }
          .also { telemetry["dpsAuthorisationId"] = it }
      } else {
        null
      }
      telemetry["dpsSentencingCourtAppearanceId"] = "$dpsSentencingId"

      val dpsCourtAppearanceId = dpsApi.syncCourtEvent(prisonerNumber, nomisSchedule.toDpsRequest(dpsSentencingId)).id
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
    DirectionCode.OUT -> syncCourtScheduleOutUpdated(event)
    // TODO when direction is added to the event put this else back in - for now we'll have to check the direction after the nomis call
    //    else -> log.info("Ignoring update of scheduled movement event ID ${event.eventId} with direction ${event.directionCode} ")
    else -> syncCourtScheduleOutUpdated(event)
  }

  suspend fun syncCourtScheduleOutUpdated(event: CourtScheduleEvent) {
    track("${TELEMETRY_PREFIX}-udpated", mutableMapOf()) {}
  }

  suspend fun courtScheduleDeleted(event: CourtScheduleEvent) = when (event.directionCode) {
    DirectionCode.OUT -> syncCourtScheduleOutDeleted(event)
    // TODO when direction is added to the event put this else back in - for now we'll have to check the direction after the nomis call
    //    else -> log.info("Ignoring delete of scheduled movement event ID ${event.eventId} with direction ${event.directionCode} ")
    else -> syncCourtScheduleOutDeleted(event)
  }

  suspend fun syncCourtScheduleOutDeleted(event: CourtScheduleEvent) {
    track("${TELEMETRY_PREFIX}-deleted", mutableMapOf()) {}
  }

  private suspend fun tryToCreateScheduleMapping(mapping: CourtScheduleMappingDto, telemetry: MutableMap<String, Any>) {
    try {
      mappingApi.createCourtScheduleMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for court schedule with NOMIS id ${mapping.nomisEventId}", e)
      queueService.sendMessage(
        messageType = RETRY_MAPPING_COURT_SCHEDULE.name,
        synchronisationType = SynchronisationType.EXTERNAL_MOVEMENTS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }
}

private fun CourtScheduleOut.toDpsRequest(sentencingCourtAppearanceId: String?) = SyncCourtEvent(
  courtEvent = CourtEvent(
    prisonCodeAtTimeOfScheduling = this.prison,
    agyLocId = court,
    eventDate = eventDate,
    startTime = "$startTime",
    courtEventType = eventType,
    eventStatus = eventStatus,
    dpsId = null,
    eventId = eventId,
    commentText = comment,
    externalReferenceUrn = sentencingCourtAppearanceId,
  ),
  occurredAt = this.audit.modifyDatetime ?: this.audit.createDatetime,
  user = SyncUser(
    username = audit.modifyUserId ?: audit.createUsername,
    // TODO this needs adding to the NOMIS DTO?
    activeCaseloadId = null,
  ),
)
