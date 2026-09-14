package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransfersRetryMappingMessageTypes.RETRY_MOVE_BOOKING_MAPPING_TRANSFER_SCHEDULER
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferMovementIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferScheduleIdMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingTransferMovements
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.MoveTransfersRequest

@Service
class TransferSchedulerMoveBookingService(
  private val nomisApi: TransferScheduleNomisApiService,
  private val mappingApi: TransferScheduleMappingApiService,
  private val dpsApi: TransferScheduleDpsApiService,
  private val queueService: SynchronisationQueueService,
  private val migrationService: TransferScheduleMigrationService,
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
      if (booking == null || booking.isEmpty()) {
        telemetry["reason"] = "No transfers found for booking=$bookingId"
        telemetryClient.trackEvent("transfer-scheduler-move-booking-ignored", telemetry)
        return
      }

      val mappings = mappingApi.getTransferScheduleMoveBookingMappings(bookingId)

      val dpsTransferIds = booking.findDpsScheduleIds(mappings.scheduleIds, telemetry)
      val dpsUnscheduledMovementIds = booking.findDpsUnscheduledMovementIds(mappings.movementIds, telemetry)
      dpsApi.moveBooking(
        MoveTransfersRequest(
          from = fromOffender,
          to = toOffender,
          transferIds = dpsTransferIds.toSet(),
          unscheduledMovementIds = dpsUnscheduledMovementIds.toSet(),
        ),
      )

      tryToMoveBookingMappings(bookingId, fromOffender, toOffender, telemetry)
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
        ?: throw TransferSchedulerMoveBookingException("No transfer schedule mapping found for eventId=$nomisEventId")
    }
    .also { telemetry["dpsTransferIds"] = "$it" }

  private fun BookingTransferMovements.findDpsUnscheduledMovementIds(
    mappings: List<TransferMovementIdMapping>,
    telemetry: MutableMap<String, Any>,
  ) = unscheduledTransferMovements
    .map { it.sequence }
    .also { telemetry["nomisUnscheduledMovementSeqs"] = "$it" }
    .map { nomisMovementSeq ->
      mappings.find { it.nomisMovementSeq == nomisMovementSeq }
        ?.dpsTransferMovementId
        ?: throw TransferSchedulerMoveBookingException("No transfer movement mapping found for bookingId=$bookingId, movementSeq=$nomisMovementSeq")
    }
    .also { telemetry["dpsUnscheduledMovementIds"] = "$it" }

  private suspend fun tryToMoveBookingMappings(bookingId: Long, fromOffenderNo: String, toOffenderNo: String, telemetry: MutableMap<String, Any>) {
    try {
      moveMappingsAndResync(bookingId, fromOffenderNo, toOffenderNo)
    } catch (e: Exception) {
      log.error("Failed to move booking mappings for bookingId=$bookingId", e)
      queueService.sendMessage(
        messageType = RETRY_MOVE_BOOKING_MAPPING_TRANSFER_SCHEDULER.name,
        synchronisationType = SynchronisationType.TRANSFER_SCHEDULER,
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
          "transfer-scheduler-move-booking-mapping-retry-updated",
          retryMessage.telemetryAttributes,
        )
      }
  }

  suspend fun moveMappingsAndResync(bookingId: Long, fromOffenderNo: String, toOffenderNo: String) {
    mappingApi.moveTransferScheduleBookingMappings(bookingId, fromOffenderNo, toOffenderNo)
    migrationService.resyncPrisonerTransferMovements(fromOffenderNo)
    migrationService.resyncPrisonerTransferMovements(toOffenderNo)
  }
}

private fun BookingTransferMovements.isEmpty() = transferSchedules.isEmpty() && unscheduledTransferMovements.isEmpty()

class TransferSchedulerMoveBookingException(message: String) : RuntimeException(message)
