package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.toDpsUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.BookingTransferMovementMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.BookingTransferScheduleMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferSchedulerBookingMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferSchedulerPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TransferSchedulerPrisonerMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTransferMovementsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonNumberAndRootOffenderId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TransferMovementOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TransferScheduleOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.TransferScheduleWaitlist
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByIdRangeMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByLastId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.TRANSFER_MOVEMENTS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.AtAndBy
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.ResyncMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.ResyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.ResyncTransfer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.ResyncTransfersRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncSchedule
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncTransfer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncWaitlist

@Service
class TransferScheduleMigrationService(
  private val transfersNomisApi: TransferScheduleNomisApiService,
  private val mappingApi: TransferScheduleMappingApiService,
  private val dpsApi: TransferScheduleDpsApiService,
  private val nomisApi: NomisApiService,
  jsonMapper: JsonMapper,
  @Value($$"${transfermovements.page.size:1000}") pageSize: Long,
  @Value($$"${transfermovements.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${transfermovements.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${transfermovements.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : ByIdRangeMigrationService<TransferSchedulerMigrationFilter, PrisonNumberAndRootOffenderId, TransferSchedulerPrisonerMappingsDto>(
  mappingService = mappingApi,
  migrationType = TRANSFER_MOVEMENTS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckRetrySeconds,
  completeCheckRetrySeconds = completeCheckCount,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {

  override suspend fun getTotalNumberOfIds(migrationFilter: TransferSchedulerMigrationFilter): Long = nomisApi.getPrisonerIds(0, 1).totalElements

  override suspend fun getRangeOfIds(
    body: TransferSchedulerMigrationFilter,
    pageSize: Long,
  ): List<Pair<PrisonNumberAndRootOffenderId, PrisonNumberAndRootOffenderId>> = nomisApi.getAllPrisonersIdRanges(pageSize)
    .map { Pair(PrisonNumberAndRootOffenderId(it.fromRootOffenderId, ""), PrisonNumberAndRootOffenderId(it.toRootOffenderId, "")) }

  override suspend fun getPageOfIdsFromIdRange(
    firstId: PrisonNumberAndRootOffenderId?,
    lastId: PrisonNumberAndRootOffenderId?,
    migrationFilter: TransferSchedulerMigrationFilter,
  ): List<PrisonNumberAndRootOffenderId> = if (migrationFilter.prisonerNumber == null) {
    nomisApi.getAllPrisonersInRange(firstId!!.rootOffenderId, lastId!!.rootOffenderId)
  } else {
    // If a single prisoner migration is requested, then we'll trust the input as we're probably testing. Pretend that we called nomis-prisoner-api which found a single prisoner.
    listOf(PrisonNumberAndRootOffenderId(0, migrationFilter.prisonerNumber))
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonNumberAndRootOffenderId>) {
    val rootOffenderId = context.body.rootOffenderId
    val offenderNo = context.body.prisonNumber
    val migrationId = context.migrationId
    val telemetry = mutableMapOf(
      "rootOffenderId" to rootOffenderId,
      "offenderNo" to offenderNo,
      "migrationId" to migrationId,
    )

    val offenderTransferMovements = transfersNomisApi.getOffenderTransferMovementsOrNull(offenderNo)
      ?: OffenderTransferMovementsResponse(offenderNo, rootOffenderId, listOf())
    val oldMappingIds = mappingApi.getMappings(offenderNo)
    val dpsResponse = dpsApi.resyncPrisoner(offenderNo, offenderTransferMovements.toDpsRequest(oldMappingIds))
      ?: ResyncResponse(listOf(), listOf())
    val mappings = offenderTransferMovements.buildMappings(offenderNo, migrationId, dpsResponse)

    createMappingOrOnFailureDo(mappings) {}
  }

  private suspend fun createMappingOrOnFailureDo(
    mappings: TransferSchedulerPrisonerMappingsDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      createMappings(mappings)
    }.onSuccess {
      publishTelemetry(
        if (it.isError) "duplicate" else "migrated",
        mapOf(
          "offenderNo" to mappings.offenderNo,
          "migrationId" to mappings.migrationId,
        ),
      )
    }.onFailure {
      failureHandler(it)
    }
  }

  private suspend fun createMappings(mappings: TransferSchedulerPrisonerMappingsDto) = mappingApi.createMapping(
    mappings,
    object : ParameterizedTypeReference<DuplicateErrorResponse<TransferSchedulerPrisonerMappingsDto>>() {},
  )

  private fun publishTelemetry(type: String, telemetry: Map<String, String>) {
    telemetryClient.trackEvent(
      "transfer-scheduler-migration-entity-$type",
      telemetry,
    )
  }

  override fun parseContextFilter(json: String): MigrationMessage<*, TransferSchedulerMigrationFilter> = jsonMapper.readValue(json)
  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<TransferSchedulerMigrationFilter, ByLastId<PrisonNumberAndRootOffenderId>>> = jsonMapper.readValue(json)
  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonNumberAndRootOffenderId> = jsonMapper.readValue(json)
  override fun parseContextMapping(json: String): MigrationMessage<*, TransferSchedulerPrisonerMappingsDto> = jsonMapper.readValue(json)
}

fun OffenderTransferMovementsResponse.toDpsRequest(oldMappingIds: TransferSchedulerPrisonerMappingIdsDto) = ResyncTransfersRequest(
  transfers = bookings.flatMap { booking ->
    booking.transferSchedules.map {
      val schedule = it.schedule
      val movement = it.movement
      ResyncTransfer(
        created = AtAndBy(schedule.audit.createDatetime, schedule.audit.createUsername.toDpsUser()),
        modified = schedule.audit.modifyDatetime?.let { AtAndBy(schedule.audit.modifyDatetime, schedule.audit.modifyUserId!!.toDpsUser()) },
        transfer = SyncTransfer(
          eventId = schedule.eventId,
          dpsId = oldMappingIds.schedules.find { it.nomisEventId == schedule.eventId }?.dpsTransferScheduleId,
          schedule = schedule.toDpsResyncRequest(),
          waitlist = schedule.waitlist?.toDpsResyncRequest(),
        ),
        movement = movement?.toDpsResyncRequest(oldMappingIds),
      )
    }
  },
  unscheduledMovements = bookings.flatMap { booking ->
    booking.unscheduledTransferMovements.map { it.toDpsResyncRequest(oldMappingIds) }
  },
)

fun TransferScheduleOut.toDpsResyncRequest() = SyncSchedule(
  eventSubType = eventSubType,
  eventStatus = eventStatus,
  agyLocId = fromPrison,
  start = startTime,
  commentText = comment,
  hiddenCommentText = hiddenComment,
  toAgyLocId = toPrison,
  outcomeReasonCode = cancellationReasonCode,
  escortCode = escortCode,
)

fun TransferScheduleWaitlist.toDpsResyncRequest() = SyncWaitlist(
  requestDate = requestDate,
  waitListStatus = status,
  statusDate = statusDate,
  transferPriority = priority,
  approved = approved,
  approvedUsername = approvedUserName,
  outcomeReasonCode = cancellationReasonCode?.let { SyncWaitlist.OutcomeReasonCode.valueOf(it) },
  commentText1 = comment,
)

fun TransferMovementOut.toDpsResyncRequest(oldMappingIds: TransferSchedulerPrisonerMappingIdsDto) = ResyncMovement(
  created = AtAndBy(audit.createDatetime, audit.createUsername.toDpsUser()),
  modified = audit.modifyDatetime?.let { AtAndBy(audit.modifyDatetime, audit.modifyUserId!!.toDpsUser()) },
  movement = SyncMovement(
    occurredAt = movementTime,
    movementReasonCode = movementReason,
    escortCode = escort ?: DEFAULT_ESCORT_CODE,
    fromAgyLocId = fromPrison,
    toAgyLocId = toPrison,
    dpsId = oldMappingIds.movements.find { it.nomisBookingId == bookingId && it.nomisMovementSeq == sequence }?.dpsTransferMovementId,
    dpsTransferId = eventId?.let { oldMappingIds.schedules.find { it.nomisEventId == eventId }?.dpsTransferScheduleId },
    offenderBookId = bookingId,
    movementSeq = sequence,
    active = active,
    commentText = commentText,
  ),
)

private fun OffenderTransferMovementsResponse.buildMappings(offenderNo: String, migrationId: String, dpsResponse: ResyncResponse) = TransferSchedulerPrisonerMappingsDto(
  offenderNo = offenderNo,
  migrationId = migrationId,
  bookings = bookings.map { booking ->
    TransferSchedulerBookingMappingsDto(
      bookingId = booking.bookingId,
      schedules = booking.transferSchedules.map { transferSchedule ->
        BookingTransferScheduleMappingsDto(
          nomisEventId = transferSchedule.schedule.eventId,
          dpsTransferScheduleId = dpsResponse.findScheduledDpsId(transferSchedule.schedule.eventId),
          movement = transferSchedule.movement?.let { movement ->
            BookingTransferMovementMappingsDto(
              nomisMovementSeq = movement.sequence,
              dpsTransferMovementId = dpsResponse.findScheduledDpsId(movement.bookingId, movement.sequence),
            )
          },
        )
      },
      unscheduledMovements = booking.unscheduledTransferMovements.map { unscheduledTransferMovement ->
        BookingTransferMovementMappingsDto(
          nomisMovementSeq = unscheduledTransferMovement.sequence,
          dpsTransferMovementId = dpsResponse.findUnscheduledDpsId(unscheduledTransferMovement.bookingId, unscheduledTransferMovement.sequence),
        )
      },
    )
  },
)

private fun ResyncResponse.findScheduledDpsId(eventId: Long) = transfers.first { mapping -> mapping.eventId == eventId }.dpsId

private fun ResyncResponse.findScheduledDpsId(bookingId: Long, sequence: Int) = transfers.mapNotNull { it.movement }.first { mapping -> mapping.offenderBookId == bookingId && mapping.movementSeq == sequence }.dpsId

private fun ResyncResponse.findUnscheduledDpsId(bookingId: Long, sequence: Int) = unscheduledMovements.first { mapping -> mapping.offenderBookId == bookingId && mapping.movementSeq == sequence }.dpsId
