package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.MoveCourtEventRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerBookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtMovementIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtScheduleIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingCourtMovements
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService

@Service
class CourtSchedulerMoveBookingService(
  private val dpsApi: CourtSchedulerDpsApiService,
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
    val telemetry = mutableMapOf<String, String>(
      "fromOffenderNo" to fromOffender,
      "toOffenderNo" to toOffender,
      "bookingId" to bookingId.toString(),
    )

    val booking = nomisApi.getBookingCourtMovementsOrNull(bookingId)!!
    val mappings = mappingApi.getCourtSchedulerMoveBookingMappings(bookingId)

    val dpsCourtAppearanceIds = booking.findDpsScheduleIds(mappings.scheduleIds)
    val dpsUnscheduleMovementIds = booking.findDpsMovementIds(mappings.movementIds)
    dpsApi.moveBooking(
      MoveCourtEventRequest(
        fromPersonIdentifier = fromOffender,
        toPersonIdentifier = toOffender,
        scheduleIds = dpsCourtAppearanceIds.toSet(),
        unscheduledMovementIds = dpsUnscheduleMovementIds.toSet(),
      ),
    )
    mappingApi.moveCourtSchedulerBookingMappings(bookingId, fromOffender, toOffender)

    migrationService.resyncPrisonerCourtMovements(fromOffender)
    migrationService.resyncPrisonerCourtMovements(toOffender)

    telemetryClient.trackEvent(
      "court-scheduler-move-booking-success",
      telemetry,
      null,
    )
  }

  private fun BookingCourtMovements.findDpsScheduleIds(
    mappings: List<CourtScheduleIdMapping>,
  ) = courtSchedules
    .map { it.eventId }
    .mapNotNull { nomisEventId ->
      mappings.find { it.nomisEventId == nomisEventId }
        ?.dpsCourtAppearanceId
    }

  private fun BookingCourtMovements.findDpsMovementIds(
    mappings: List<CourtMovementIdMapping>,
  ) = (unscheduledCourtMovementOuts.map { it.sequence } + unscheduledCourtMovementIns.map { it.sequence })
    .mapNotNull { nomisMovementSeq ->
      mappings.find { it.nomisMovementSeq == nomisMovementSeq }
        ?.dpsCourtMovementId
    }
}
