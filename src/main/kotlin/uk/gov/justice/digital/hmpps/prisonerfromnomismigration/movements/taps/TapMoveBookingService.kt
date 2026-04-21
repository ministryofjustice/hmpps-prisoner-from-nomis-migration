package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.model.MoveTemporaryAbsencesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceApplicationIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TemporaryAbsenceMovementIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingTaps
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class TapMoveBookingService(
  private val dpsApi: TapDpsApiService,
  private val nomisApi: TapsNomisApiService,
  private val mappingApi: ExternalMovementsMappingApiService,
  private val queueService: SynchronisationQueueService,
  private val migrationService: TapMigrationService,
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

    track("temporary-absence-move-booking", telemetry) {
      val booking = getNomisBooking(toOffender, bookingId)
      if (booking == null || booking.isEmpty()) {
        telemetry["reason"] = "No TAPs to move for booking=$bookingId"
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

  private suspend fun getNomisBooking(offenderNo: String, bookingId: Long) = nomisApi.getAllOffenderTapsOrNull(offenderNo)
    ?.bookings
    ?.firstOrNull { it.bookingId == bookingId }

  private fun BookingTaps.findDpsAuthorisationIds(
    mappings: List<TemporaryAbsenceApplicationIdMapping>,
    telemetry: MutableMap<String, Any>,
  ) = tapApplications
    .map { it.tapApplicationId }
    .also { telemetry["nomisApplicationIds"] = "$it" }
    .map { nomisApplicationId ->
      mappings.find { it.applicationId == nomisApplicationId }
        ?.authorisationId
        ?: throw TapMoveBookingException("No mapping found for bookingId=$bookingId, movementApplicationId=$nomisApplicationId")
    }
    .also { telemetry["dpsAuthorisationIds"] = "$it" }

  private fun BookingTaps.findDpsMovementIds(
    mappings: List<TemporaryAbsenceMovementIdMapping>,
    telemetry: MutableMap<String, Any>,
  ) = (unscheduledTapMovementOuts.map { it.sequence } + unscheduledTapMovementIns.map { it.sequence })
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

  private fun BookingTaps.isEmpty() = this.tapApplications.isEmpty() &&
    this.unscheduledTapMovementOuts.isEmpty() &&
    this.unscheduledTapMovementIns.isEmpty()
}

class TapMoveBookingException(message: String) : RuntimeException(message)
