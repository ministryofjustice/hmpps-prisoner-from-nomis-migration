package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.BookingMovedAdditionalInformationEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerBookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsSyncService.Companion.log
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MoveTemporaryAbsencesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceMovementIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingTemporaryAbsences
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import kotlin.String

@Service
class ExternalMovementsMoveBookingService(
  private val dpsApi: ExternalMovementsDpsApiService,
  private val nomisApi: ExternalMovementsNomisApiService,
  private val mappingApi: ExternalMovementsMappingApiService,
  private val queueService: SynchronisationQueueService,
  private val migrationService: ExternalMovementsMigrationService,
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {

  suspend fun moveBooking(request: PrisonerBookingMovedDomainEvent) {
    val (toOffender, fromOffender, bookingId) = request.additionalInformation
    val telemetry = mutableMapOf<String, Any>(
      "fromOffenderNo" to fromOffender,
      "toOffenderNo" to toOffender,
      "bookingId" to bookingId,
    )

    track("temporary-absence-move-booking", telemetry) {
      val booking = getNomisBooking(toOffender, bookingId)
      if (booking.isEmpty()) {
        telemetry["reason"] = "No application or unscheduled mappings to move for booking=$bookingId"
        telemetryClient.trackEvent("temporary-absence-move-booking-ignored", telemetry)
        return
      }

      val mappings = mappingApi.getMoveBookingMappings(bookingId)

      val dpsAuthorisationIds = booking.findDpsAuthorisationIds(mappings.applicationIds, telemetry)
      val dpsUnscheduleMovementIds = booking.findDpsMovementIds(mappings.movementIds, telemetry)
      dpsApi.moveBooking(
        MoveTemporaryAbsencesRequest(
          fromPersonIdentifier = fromOffender,
          toPersonIdentifier = toOffender,
          authorisationIds = dpsAuthorisationIds.toSet(),
          unscheduledMovementIds = dpsUnscheduleMovementIds.toSet(),
        ),
      )
      tryToMoveBookingMappings(bookingId, fromOffender, toOffender, telemetry)
    }
  }

  private suspend fun getNomisBooking(offenderNo: String, bookingId: Long) = nomisApi.getTemporaryAbsencesOrNull(offenderNo)
    ?.bookings
    ?.firstOrNull { it.bookingId == bookingId }
    ?: throw TapMoveBookingException("No booking found in NOMIS for bookingId=$bookingId")

  private fun BookingTemporaryAbsences.findDpsAuthorisationIds(
    mappings: List<TemporaryAbsenceApplicationIdMapping>,
    telemetry: MutableMap<String, Any>,
  ) = temporaryAbsenceApplications
    .map { it.movementApplicationId }
    .also { telemetry["nomisApplicationIds"] = "$it" }
    .map { nomisApplicationId ->
      mappings.find { it.applicationId == nomisApplicationId }
        ?.authorisationId
        ?: throw TapMoveBookingException("No mapping found for bookingId=$bookingId, movementApplicationId=$nomisApplicationId")
    }
    .also { telemetry["dpsAuthorisationIds"] = "$it" }

  private fun BookingTemporaryAbsences.findDpsMovementIds(
    mappings: List<TemporaryAbsenceMovementIdMapping>,
    telemetry: MutableMap<String, Any>,
  ) = (unscheduledTemporaryAbsences.map { it.sequence } + unscheduledTemporaryAbsenceReturns.map { it.sequence })
    .also { telemetry["nomisUnscheduledMovementSeqs"] = "$it" }
    .map { nomisMovementSeq ->
      mappings.find { it.movementSeq == nomisMovementSeq }
        ?.dpsMovementId
        ?: throw TapMoveBookingException("No mapping found for bookingId=$bookingId, movementSeq=$nomisMovementSeq")
    }
    .also { telemetry["dpsUnscheduledMovementIds"] = "$it" }

  private suspend fun tryToMoveBookingMappings(bookingId: Long, fromOffenderNo: String, toOffenderNo: String, telemetry: MutableMap<String, Any>) {
    try {
      moveMappingsAndResync(bookingId, fromOffenderNo, toOffenderNo)
    } catch (e: Exception) {
      log.error("Failed to move booking mappings for bookingId=$bookingId", e)
      queueService.sendMessage(
        messageType = ExternalMovementRetryMappingMessageTypes.RETRY_MOVE_BOOKING_MAPPING_TEMPORARY_ABSENCE.name,
        synchronisationType = SynchronisationType.EXTERNAL_MOVEMENTS,
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
          "temporary-absence-move-booking-mapping-retry-updated",
          retryMessage.telemetryAttributes,
        )
      }
  }

  suspend fun moveMappingsAndResync(bookingId: Long, fromOffenderNo: String, toOffenderNo: String) {
    mappingApi.moveBookingMappings(bookingId, fromOffenderNo, toOffenderNo)
    migrationService.resyncPrisonerTaps(fromOffenderNo)
    migrationService.resyncPrisonerTaps(toOffenderNo)
  }

  private fun BookingTemporaryAbsences.isEmpty() = this.temporaryAbsenceApplications.isEmpty() &&
    this.unscheduledTemporaryAbsences.isEmpty() &&
    this.unscheduledTemporaryAbsenceReturns.isEmpty()
}

class TapMoveBookingException(message: String) : RuntimeException(message)
