package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.DirectionCode.OUT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.MovementType.TRN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ScheduledMovementEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapScheduleService.Companion.log
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferScheduleMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TransferScheduleOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncSchedule
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncTransfer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncTransferRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncWaitlist

private const val TELEMETRY_PREFIX: String = "${TRANSFER_TELEMETRY_PREFIX}-schedule"

@Service
class TransferScheduleSyncScheduleService(
  private val mappingApiService: TransferScheduleMappingApiService,
  private val nomisApiService: TransferScheduleNomisApiService,
  private val dpsApiService: TransferScheduleDpsApiService,
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {

  suspend fun scheduledMovementInserted(event: ScheduledMovementEvent) = when (event.eventMovementType) {
    TRN if (event.directionCode == OUT) -> syncTransferScheduleOutInserted(event)
    else -> log.info("Ignoring insert of scheduled movement event ID ${event.eventId} with type ${event.eventMovementType} and direction ${event.directionCode} ")
  }

  suspend fun syncTransferScheduleOutInserted(event: ScheduledMovementEvent) {
    val (eventId, bookingId, prisonerNumber) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisEventId" to eventId,
    )

    mappingApiService.getTransferScheduleMappingOrNull(eventId)
      ?.also { telemetryClient.trackEvent("${TELEMETRY_PREFIX}-inserted-ignored", telemetry) }
      ?: run {
        track("${TELEMETRY_PREFIX}-inserted", telemetry) {
          syncTransferScheduleOut(prisonerNumber, eventId, telemetry)
            // TODO handle mapping retries
            //  ?.also { tryToCreateScheduledMovementMapping(it, telemetry) }
            .also { mappingApiService.createTransferScheduleMapping(it) }
        }
      }
  }

  suspend fun syncTransferScheduleOut(prisonerNumber: String, eventId: Long, telemetry: MutableMap<String, Any>): TransferScheduleMappingDto {
    val nomis = nomisApiService.getTransferScheduleOut(prisonerNumber, eventId)
    val dpsId = dpsApiService.syncTransferSchedule(prisonerNumber, nomis.toDpsRequest()).dpsId
      .also { telemetry["dpsTransferScheduleId"] = it }
    return TransferScheduleMappingDto(prisonerNumber, nomis.bookingId, eventId, dpsId, NOMIS_CREATED)
  }
}

fun TransferScheduleOut.toDpsRequest() = SyncTransferRequest(
  transfer = SyncTransfer(
    eventId = eventId,
    schedule = SyncSchedule(
      start = startTime,
      eventSubType = eventSubType,
      eventStatus = eventStatus,
      commentText = comment,
      hiddenCommentText = hiddenComment,
      agyLocId = fromPrison,
      toAgyLocId = toPrison,
      outcomeReasonCode = cancellationReasonCode,
      escortCode = escortCode,
    ),
    waitlist = waitlist?.let { waitlist ->
      SyncWaitlist(
        requestDate = waitlist.requestDate,
        waitListStatus = waitlist.status,
        statusDate = waitlist.statusDate,
        transferPriority = waitlist.priority,
        approved = waitlist.approved,
        approvedUsername = waitlist.approvedUserName,
        outcomeReasonCode = waitlist.cancellationReasonCode?.let { SyncWaitlist.OutcomeReasonCode.valueOf(it) },
        commentText1 = waitlist.comment,
      )
    },
  ),
  occurredAt = audit.modifyDatetime ?: audit.createDatetime,
  syncUser = SyncUser(audit.modifyUserId ?: audit.createUsername, userActiveCaseloadId),
)
