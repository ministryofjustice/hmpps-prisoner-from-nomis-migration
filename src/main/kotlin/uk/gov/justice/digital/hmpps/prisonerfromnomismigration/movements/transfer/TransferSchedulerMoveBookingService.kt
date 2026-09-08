package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerBookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerMoveBookingException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferScheduleIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingTransferMovements
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.MoveTransfersRequest

@Service
class TransferSchedulerMoveBookingService(
  private val nomisApi: TransferScheduleNomisApiService,
  private val mappingApi: TransferScheduleMappingApiService,
  private val dpsApi: TransferScheduleDpsApiService,
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

    track("transfer-scheduler-move-booking", telemetry) {
      val booking = nomisApi.getBookingTransferMovementsOrNull(bookingId)
      if (booking == null) {
        telemetry["reason"] = "No transfers found for booking=$bookingId"
        telemetryClient.trackEvent("transfer-scheduler-move-booking-ignored", telemetry)
        return
      }

      val mappings = mappingApi.getTransferScheduleMoveBookingMappings(bookingId)

      val dpsTransferIds = booking.findDpsScheduleIds(mappings.scheduleIds, telemetry)
      dpsApi.moveBooking(
        MoveTransfersRequest(
          from = fromOffender,
          to = toOffender,
          transferIds = dpsTransferIds.toSet(),
        ),
      )

      mappingApi.moveTransferScheduleBookingMappings(bookingId, fromOffender, toOffender)
    }
  }

  private fun BookingTransferMovements.findDpsScheduleIds(
    mappings: List<TransferScheduleIdMapping>,
    telemetry: MutableMap<String, Any>,
  ) = transferSchedules
    .map { it.schedule.eventId }
    .also { telemetry["nomisEventIds"] = "$it" }
    .map { nomisEventId ->
      mappings.find { it.nomisEventId == nomisEventId }
        ?.dpsTransferScheduleId
        ?: throw CourtSchedulerMoveBookingException("No transfer schedule mapping found for eventId=$nomisEventId")
    }
    .also { telemetry["dpsTransferIds"] = "$it" }
}
