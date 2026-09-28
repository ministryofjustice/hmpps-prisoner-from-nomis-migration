package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.BookingMovedAdditionalInformationEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerBookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtMovementRetryMappingMessageTypes.RETRY_MOVE_BOOKING_MAPPING_COURT_SCHEDULER
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtMovementIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtScheduleIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingCourtMovements
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class CourtSchedulerMoveBookingService(
  private val nomisApi: CourtSchedulerNomisApiService,
  private val mappingApi: CourtSchedulerMappingApiService,
  private val queueService: SynchronisationQueueService,
  private val migrationService: CourtSchedulerMigrationService,
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun moveBooking(request: PrisonerBookingMovedDomainEvent) {
    val (toOffender, fromOffender, bookingId) = request.additionalInformation
    val telemetry = mutableMapOf<String, Any>(
      "fromOffenderNo" to fromOffender,
      "toOffenderNo" to toOffender,
      "bookingId" to bookingId,
    )

    track("court-scheduler-move-booking", telemetry) {
      val booking = nomisApi.getBookingCourtMovementsOrNull(bookingId)
      if (booking == null || booking.isEmpty()) {
        telemetry["reason"] = "No court movements found for booking=$bookingId"
        telemetryClient.trackEvent("court-scheduler-move-booking-ignored", telemetry)
        return
      }

      val mappings = mappingApi.getCourtSchedulerMoveBookingMappings(bookingId)

      val nomisEventIds = booking.findNomisEventIds()
      val dpsCourtAppearanceIds = booking.findDpsScheduleIds(mappings.scheduleIds)
      val nomisMovementSeqs = mappings.movementIds.map { it.nomisMovementSeq }
      val dpsMovementIds = booking.findDpsMovementIds(mappings.movementIds)
      telemetry.apply {
        put("nomisEventIds", nomisEventIds)
        put("dpsCourtAppearanceIds", dpsCourtAppearanceIds)
        put("nomisMovementSeqs", nomisMovementSeqs)
        put("dpsMovementIds", dpsMovementIds)
      }

      tryToMoveBookingMappings(bookingId, fromOffender, toOffender, telemetry.mapValues { (_, value) -> value.toString() })
    }
  }

  private fun BookingCourtMovements.findNomisEventIds() = courtSchedules
    .filter { it.courtCaseId == null }
    .map { it.eventId }

  private fun BookingCourtMovements.findDpsScheduleIds(
    mappings: List<CourtScheduleIdMapping>,
  ) = findNomisEventIds()
    .map { nomisEventId ->
      mappings.find { it.nomisEventId == nomisEventId }
        ?.dpsCourtAppearanceId
        ?: throw CourtSchedulerMoveBookingException("No court schedule mapping found for eventId=$nomisEventId")
    }

  private fun BookingCourtMovements.findNomisMovementSeqs() = buildList {
    addAll(courtSchedules.mapNotNull { it.courtMovementOut?.sequence })
    addAll(courtSchedules.mapNotNull { it.courtMovementIn?.sequence })
    addAll(unscheduledCourtMovementOuts.map { it.sequence })
    addAll(unscheduledCourtMovementIns.map { it.sequence })
  }

  private fun BookingCourtMovements.findDpsMovementIds(
    mappings: List<CourtMovementIdMapping>,
  ) = findNomisMovementSeqs().map { nomisMovementSeq ->
    nomisMovementSeq to
      (
        mappings.find { it.nomisMovementSeq == nomisMovementSeq }
          ?.dpsCourtMovementId
          ?: throw CourtSchedulerMoveBookingException("No court movement mapping found for bookingId=$bookingId, movementSeq=$nomisMovementSeq")
        )
  }

  private suspend fun tryToMoveBookingMappings(
    bookingId: Long,
    fromOffenderNo: String,
    toOffenderNo: String,
    telemetry: Map<String, String>,
  ) {
    try {
      moveMappingsAndResync(bookingId, fromOffenderNo, toOffenderNo)
    } catch (e: Exception) {
      log.error("Failed to move booking mappings for bookingId=$bookingId", e)
      queueService.sendMessage(
        messageType = RETRY_MOVE_BOOKING_MAPPING_COURT_SCHEDULER.name,
        synchronisationType = SynchronisationType.COURT_SCHEDULER,
        message = BookingMovedAdditionalInformationEvent(toOffenderNo, fromOffenderNo, bookingId),
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryMoveBookingMapping(retryMessage: InternalMessage<BookingMovedAdditionalInformationEvent>) {
    val (toOffenderNo, fromOffenderNo, bookingId) = retryMessage.body
    moveMappingsAndResync(bookingId, fromOffenderNo, toOffenderNo)
      .also {
        telemetryClient.trackEvent(
          "court-scheduler-move-booking-mapping-retry-updated",
          retryMessage.telemetryAttributes,
        )
      }
  }

  suspend fun moveMappingsAndResync(bookingId: Long, fromOffenderNo: String, toOffenderNo: String) {
    mappingApi.moveCourtSchedulerBookingMappings(bookingId, fromOffenderNo, toOffenderNo)
    migrationService.resyncPrisonerCourtMovements(fromOffenderNo)
    migrationService.resyncPrisonerCourtMovements(toOffenderNo)
  }
}

private fun BookingCourtMovements.isEmpty() = courtSchedules.isEmpty() &&
  unscheduledCourtMovementOuts.isEmpty() &&
  unscheduledCourtMovementIns.isEmpty()

class CourtSchedulerMoveBookingException(message: String) : RuntimeException(message)
