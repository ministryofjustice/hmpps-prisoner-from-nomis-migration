package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited.Companion.DPS_SYNC_AUDIT_MODULE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.DirectionCode.OUT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.MovementType.TRN
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ScheduledMovementEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.toDpsUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransfersRetryMappingMessageTypes.RETRY_MAPPING_TRANSFER_SCHEDULE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferScheduleMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TransferScheduleOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncSchedule
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncTransfer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncTransferRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncWaitlist
import java.util.*

private const val TELEMETRY_PREFIX: String = "${TRANSFER_TELEMETRY_PREFIX}-schedule"

@Service
class TransferScheduleSyncScheduleService(
  private val mappingApiService: TransferScheduleMappingApiService,
  private val nomisApiService: TransferScheduleNomisApiService,
  private val dpsApiService: TransferScheduleDpsApiService,
  private val queueService: SynchronisationQueueService,
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {
  companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

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
    if (event.auditExactMatchOrHasMissingAudit(DPS_SYNC_AUDIT_MODULE)) {
      telemetryClient.trackEvent("${TELEMETRY_PREFIX}-inserted-ignored", telemetry)
      return
    }

    mappingApiService.getTransferScheduleMappingOrNull(eventId)
      ?.also { telemetryClient.trackEvent("${TELEMETRY_PREFIX}-inserted-ignored", telemetry) }
      ?: run {
        track("${TELEMETRY_PREFIX}-inserted", telemetry) {
          syncTransferScheduleOut(prisonerNumber, eventId, telemetry)
            .also { tryToCreateScheduleMapping(it, telemetry) }
        }
      }
  }

  suspend fun scheduledMovementUpdated(event: ScheduledMovementEvent) = when (event.eventMovementType) {
    TRN if (event.directionCode == OUT) -> syncTransferScheduleOutUpdated(event)
    else -> log.info("Ignoring update of scheduled movement event ID ${event.eventId} with type ${event.eventMovementType} and direction ${event.directionCode} ")
  }

  suspend fun syncTransferScheduleOutUpdated(event: ScheduledMovementEvent) {
    val (eventId, bookingId, prisonerNumber) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisEventId" to eventId,
    )
    if (event.auditExactMatchOrHasMissingAudit(DPS_SYNC_AUDIT_MODULE)) {
      telemetryClient.trackEvent("${TELEMETRY_PREFIX}-updated-ignored", telemetry)
      return
    }

    track("${TELEMETRY_PREFIX}-updated", telemetry) {
      val existingMapping = mappingApiService.getTransferScheduleMappingOrNull(eventId)
        ?.also { telemetry["dpsTransferScheduleId"] = it.dpsTransferScheduleId }
        ?: throw IllegalStateException("No mapping found when handling an update event for scheduled transfer $eventId - hopefully messages are being processed out of order and this event will succeed on a retry once the create event is processed. Otherwise we need to understand why the original create event was never processed.")

      syncTransferScheduleOut(prisonerNumber, eventId, telemetry, existingMapping)
    }
  }

  suspend fun syncTransferScheduleOut(
    prisonerNumber: String,
    eventId: Long,
    telemetry: MutableMap<String, Any>,
    existingMapping: TransferScheduleMappingDto? = null,
  ): TransferScheduleMappingDto {
    val nomis = nomisApiService.getTransferScheduleOut(prisonerNumber, eventId)
    val dpsId = dpsApiService.syncTransferSchedule(prisonerNumber, nomis.toDpsRequest(existingMapping?.dpsTransferScheduleId)).dpsId
      .also { telemetry["dpsTransferScheduleId"] = it }
    return TransferScheduleMappingDto(prisonerNumber, nomis.bookingId, eventId, dpsId, NOMIS_CREATED)
  }

  suspend fun transferScheduleDeleted(event: ScheduledMovementEvent) = when (event.eventMovementType) {
    TRN if (event.directionCode == OUT) -> transferScheduleOutDeleted(event)
    else -> log.info("Ignoring delete of transfer schedule event ID ${event.eventId} with type ${event.eventMovementType} and direction ${event.directionCode} ")
  }

  suspend fun transferScheduleOutDeleted(event: ScheduledMovementEvent) {
    val (eventId, bookingId, prisonerNumber) = event
    val telemetry = mutableMapOf<String, Any>(
      "offenderNo" to prisonerNumber,
      "bookingId" to bookingId,
      "nomisEventId" to eventId,
    )

    if (event.auditExactMatchOrHasMissingAudit(DPS_SYNC_AUDIT_MODULE)) {
      telemetryClient.trackEvent("${TELEMETRY_PREFIX}-deleted-ignored", telemetry)
      return
    }

    mappingApiService.getTransferScheduleMappingOrNull(eventId)
      ?.also {
        track("${TELEMETRY_PREFIX}-deleted", telemetry) {
          telemetry["dpsTransferScheduleId"] = it.dpsTransferScheduleId
          dpsApiService.deleteTransferSchedule(it.dpsTransferScheduleId)
          mappingApiService.deleteTransferScheduleMapping(eventId)
        }
      } ?: run { telemetryClient.trackEvent("${TELEMETRY_PREFIX}-deleted-ignored", telemetry) }
  }

  private suspend fun tryToCreateScheduleMapping(mapping: TransferScheduleMappingDto, telemetry: MutableMap<String, Any>) {
    try {
      createScheduleMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for transfer schedule with NOMIS id ${mapping.nomisEventId}", e)
      queueService.sendMessage(
        messageType = RETRY_MAPPING_TRANSFER_SCHEDULE.name,
        synchronisationType = SynchronisationType.TRANSFER_SCHEDULER,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  suspend fun retryCreateScheduleMapping(retryMessage: InternalMessage<TransferScheduleMappingDto>) {
    createScheduleMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent(
          "${TELEMETRY_PREFIX}-mapping-retry-created",
          retryMessage.telemetryAttributes,
        )
      }
  }

  private suspend fun createScheduleMapping(mapping: TransferScheduleMappingDto) {
    val mappingResponse = mappingApiService.createTransferScheduleMapping(mapping)
    if (mappingResponse.isError) {
      with(mappingResponse.errorResponse!!.moreInfo) {
        telemetryClient.trackEvent(
          "${TELEMETRY_PREFIX}-inserted-duplicate",
          mapOf(
            "existingOffenderNo" to existing!!.prisonerNumber,
            "existingBookingId" to existing.bookingId,
            "existingNomisEventId" to existing.nomisEventId,
            "existingDpsTransferScheduleId" to existing.dpsTransferScheduleId,
            "duplicateOffenderNo" to duplicate.prisonerNumber,
            "duplicateBookingId" to duplicate.bookingId,
            "duplicateNomisEventId" to duplicate.nomisEventId,
            "duplicateDpsTransferScheduleId" to duplicate.dpsTransferScheduleId,
          ),
        )
      }
    }
  }
}

fun TransferScheduleOut.toDpsRequest(dpsId: UUID? = null): SyncTransferRequest {
  val (waitlistOccurredAt, waitlistUser) = waitlist?.audit?.modifyDatetime
    ?.let { waitlist.audit.modifyDatetime to waitlist.audit.modifyUserId!! }
    ?: (waitlist?.audit?.createDatetime to waitlist?.audit?.createUsername)
  val (scheduleOccurredAt, scheduleUser) = audit.modifyDatetime
    ?.let { audit.modifyDatetime to audit.modifyUserId!! }
    ?: (audit.createDatetime to audit.createUsername)
  val useWaitlist = waitlistOccurredAt != null && waitlistOccurredAt > scheduleOccurredAt
  return SyncTransferRequest(
    transfer = SyncTransfer(
      dpsId = dpsId,
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
    occurredAt = if (useWaitlist) waitlistOccurredAt else scheduleOccurredAt,
    syncUser = SyncUser((if (useWaitlist) waitlistUser!! else scheduleUser).toDpsUser(), userActiveCaseloadId),
  )
}
