package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerBookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MoveTemporaryAbsencesRequest

@Service
class ExternalMovementsMoveBookingService(
  private val dpsApi: ExternalMovementsDpsApiService,
  private val nomisApi: ExternalMovementsNomisApiService,
  private val mappingApi: ExternalMovementsMappingApiService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun moveBooking(request: PrisonerBookingMovedDomainEvent) {
    val (toOffender, fromOffender, bookingId) = request.additionalInformation
    val telemetry = mutableMapOf(
      "fromOffenderNo" to fromOffender,
      "toOffenderNo" to toOffender,
      "bookingId" to "$bookingId",
    )

    // TODO SDIT-3298 tidy this up, add error handling, add retry if mapping update fails
    val nomisTaps = nomisApi.getTemporaryAbsencesOrNull(toOffender)
    val booking = nomisTaps?.bookings
      ?.firstOrNull { it.bookingId == bookingId }
      ?: throw IllegalStateException("No booking found for bookingId $bookingId")
    val mappings = mappingApi.getMoveBookingMappings(bookingId)
    val nomisApplications = booking.temporaryAbsenceApplications.map { it.movementApplicationId }
      .also { telemetry["nomisApplicationIds"] = "$it" }
    val nomisUnscheduledMovementSeqs = (booking.unscheduledTemporaryAbsences.map { it.sequence } + booking.unscheduledTemporaryAbsenceReturns.map { it.sequence })
      .also { telemetry["nomisUnscheduledMovementSeqs"] = "$it" }
    val dpsAuthorisationIds = nomisApplications.map { nomisId ->
      mappings.applicationIds.find { it.applicationId == nomisId }
        ?.authorisationId
        ?: throw IllegalStateException("No mapping found for bookingId=$bookingId, movementApplicationId=$nomisId")
    }.also {
      telemetry["dpsAuthorisationIds"] = "$it"
    }
    val dpsUnscheduleMovementIds = nomisUnscheduledMovementSeqs.map { nomisId ->
      mappings.movementIds.find { it.movementSeq == nomisId }
        ?.dpsMovementId
        ?: throw IllegalStateException("No mapping found for bookingId=$bookingId, movementSeq=$nomisId")
    }.also {
      telemetry["dpsUnscheduledMovementIds"] = "$it"
    }
    dpsApi.moveBooking(
      MoveTemporaryAbsencesRequest(
        fromPersonIdentifier = fromOffender,
        toPersonIdentifier = toOffender,
        authorisationIds = dpsAuthorisationIds.toSet(),
        unscheduledMovementIds = dpsUnscheduleMovementIds.toSet(),
      ),
    )
    mappingApi.moveBookingMappings(bookingId, fromOffender, toOffender)
    telemetryClient.trackEvent("temporary-absence-move-booking-success", telemetry, null)
  }
}
