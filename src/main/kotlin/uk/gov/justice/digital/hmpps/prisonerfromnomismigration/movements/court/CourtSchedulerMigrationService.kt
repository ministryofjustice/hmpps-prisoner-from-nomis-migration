package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.AtAndBy
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.CourtEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.CourtEventMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ResyncCourtEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ResyncCourtEventMovement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ResyncCourtEvents
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtscheduler.model.ResyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.generateBatchId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.BookingCourtMovementMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.BookingCourtScheduleMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtSchedulerBookingMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtSchedulerPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtSchedulerPrisonerMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingCourtMovementIn
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingCourtMovementOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderCourtMovementsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumberMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class CourtSchedulerMigrationService(
  private val nomisApi: CourtSchedulerNomisApiService,
  val nomisIdsApi: NomisApiService,
  private val mappingApi: CourtSchedulerMappingApiService,
  private val sentencingMappingApi: CourtSentencingMappingApiService,
  private val dpsApi: CourtSchedulerDpsApiService,
  jsonMapper: JsonMapper,
  @Value($$"${courtmovements.page.size:1000}") pageSize: Long,
  @Value($$"${courtmovements.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${courtmovements.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${courtmovements.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds:10}") completeCheckScheduledRetrySeconds: Int,
) : ByPageNumberMigrationService<CourtSchedulerMigrationFilter, PrisonerId, CourtSchedulerPrisonerMappingsDto>(
  mappingService = mappingApi,
  migrationType = MigrationType.COURT_SCHEDULER,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {

  suspend fun getIds(
    migrationFilter: CourtSchedulerMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<PrisonerId> = if (migrationFilter.prisonerNumber.isNullOrEmpty()) {
    nomisIdsApi.getPrisonerIds(
      pageNumber = pageNumber,
      pageSize = pageSize,
    )
  } else {
    // If a single prisoner migration is requested, then we'll trust the input as we're probably testing. Pretend that we called nomis-prisoner-api which found a single prisoner.
    PageImpl(mutableListOf(PrisonerId(migrationFilter.prisonerNumber)), Pageable.ofSize(1), 1)
  }

  override suspend fun getPageOfIds(
    migrationFilter: CourtSchedulerMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): List<PrisonerId> = getIds(migrationFilter, pageSize, pageNumber).content

  override suspend fun getTotalNumberOfIds(migrationFilter: CourtSchedulerMigrationFilter): Long = getIds(migrationFilter, 1, 0).totalElements

  suspend fun resyncPrisonerCourtMovements(prisonerNumber: String) = migrateNomisEntity(
    MigrationContext(
      MigrationType.COURT_SCHEDULER,
      generateBatchId(),
      1,
      PrisonerId(prisonerNumber),
      mutableMapOf("ignoreMissingCourtMovements" to false),
    ),
  )

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonerId>) {
    val offenderNo = context.body.offenderNo
    val migrationId = context.migrationId
    val telemetry = mutableMapOf(
      "offenderNo" to offenderNo,
      "migrationId" to migrationId,
    )
    val ignoreMissingCourtMovements = context.properties["ignoreMissingCourtMovements"] as Boolean? ?: true

    runCatching {
      val offenderCourtMovements = nomisApi.getOffenderCourtMovementsOrNull(offenderNo)
        ?: OffenderCourtMovementsResponse(bookings = listOf())
      if (ignoreMissingCourtMovements && offenderCourtMovements.bookings.isEmpty()) {
        publishTelemetry("ignored", telemetry.apply { this["reason"] = "The offender has no court movements" })
        return
      }

      val oldMappingIds = mappingApi.getCourtSchedulerPrisonMappingIds(offenderNo)
      val courtSentencingMappings = offenderCourtMovements.findCourtAppearanceIds()
        .let { courtAppearanceIds -> sentencingMappingApi.getAllCourtAppearancesByNomisIds(courtAppearanceIds) }
      val dpsResponse =
        dpsApi.resyncPrisoner(offenderNo, offenderCourtMovements.toDpsRequest(oldMappingIds, courtSentencingMappings))
          ?: ResyncResponse(listOf(), listOf())
      val mappings = offenderCourtMovements.buildMappings(offenderNo, migrationId, dpsResponse)

      createMappingOrOnFailureDo(mappings) {
        requeueCreateMapping(mappings, context)
      }
    }
      .onFailure {
        publishTelemetry("failed", telemetry.apply { this["reason"] = it.message ?: "Unknown error" })
        throw it
      }
  }

  override suspend fun retryCreateMapping(context: MigrationContext<CourtSchedulerPrisonerMappingsDto>) {
    createMappingOrOnFailureDo(context.body) {
      throw it
    }
  }

  private suspend fun createMappingOrOnFailureDo(
    mapping: CourtSchedulerPrisonerMappingsDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      createMapping(mapping)
    }.onSuccess {
      publishTelemetry(
        if (it.isError) "duplicate" else "migrated",
        mapOf(
          "offenderNo" to mapping.offenderNo,
          "migrationId" to mapping.migrationId,
        ),
      )
    }.onFailure {
      failureHandler(it)
    }
  }

  private suspend fun createMapping(mapping: CourtSchedulerPrisonerMappingsDto) = mappingApi.createMapping(
    mapping,
    object :
      ParameterizedTypeReference<DuplicateErrorResponse<CourtSchedulerPrisonerMappingsDto>>() {},
  )

  private suspend fun requeueCreateMapping(
    mapping: CourtSchedulerPrisonerMappingsDto,
    context: MigrationContext<*>,
  ) {
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = mapping,
      ),
    )
  }

  private fun publishTelemetry(type: String, telemetry: Map<String, String>) {
    telemetryClient.trackEvent(
      "court-scheduler-migration-entity-$type",
      telemetry,
    )
  }

  override fun parseContextFilter(json: String): MigrationMessage<*, CourtSchedulerMigrationFilter> = jsonMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<CourtSchedulerMigrationFilter, ByPageNumber>> = jsonMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonerId> = jsonMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, CourtSchedulerPrisonerMappingsDto> = jsonMapper.readValue(json)
}

private fun OffenderCourtMovementsResponse.findCourtAppearanceIds(): List<Long> = bookings
  .flatMap { booking ->
    booking.courtSchedules
      .filter { schedule -> schedule.courtCaseId != null }
      .map { schedule -> schedule.eventId }
  }

fun OffenderCourtMovementsResponse.toDpsRequest(
  oldMappingIds: CourtSchedulerPrisonerMappingIdsDto,
  sentencingMappings: List<CourtAppearanceMappingDto>,
) = ResyncCourtEvents(
  courtEvents = bookings.flatMap { booking ->
    booking.courtSchedules.map { schedule ->
      ResyncCourtEvent(
        courtEvent = CourtEvent(
          dpsId = oldMappingIds.schedules.find { it.nomisEventId == schedule.eventId }?.dpsCourtAppearanceId,
          prisonCodeAtTimeOfScheduling = schedule.prison,
          agyLocId = schedule.court,
          start = schedule.startTime,
          courtEventType = schedule.eventType,
          eventStatus = schedule.eventStatus,
          eventId = schedule.eventId,
          commentText = schedule.comment,
          externalReferenceUrn = sentencingMappings.find { it.nomisCourtAppearanceId == schedule.eventId }
            ?.dpsCourtAppearanceId
            ?.let { "$EXTERNAL_REF_PREFIX$it" },
        ),
        created = AtAndBy(schedule.audit.createDatetime, schedule.audit.createUsername),
        modified = schedule.audit.modifyDatetime?.let { AtAndBy(schedule.audit.modifyDatetime, schedule.audit.modifyUserId!!) },
        movements = listOfNotNull(
          schedule.courtMovementOut?.toDpsRequest(oldMappingIds, booking.bookingId, schedule.eventId),
          schedule.courtMovementIn?.toDpsRequest(oldMappingIds, booking.bookingId, schedule.eventId),
        ),
      )
    }
  },
  unscheduledMovements = bookings.flatMap { booking ->
    booking.unscheduledCourtMovementOuts.map { movementOut ->
      movementOut.toDpsRequest(oldMappingIds, booking.bookingId, null)
    } +
      booking.unscheduledCourtMovementIns.map { movementIn ->
        movementIn.toDpsRequest(oldMappingIds, booking.bookingId, null)
      }
  },
)

private fun BookingCourtMovementOut.toDpsRequest(
  oldMappingIds: CourtSchedulerPrisonerMappingIdsDto,
  bookingId: Long,
  eventId: Long?,
): ResyncCourtEventMovement = ResyncCourtEventMovement(
  movement = CourtEventMovement(
    dpsId = oldMappingIds.movements.find { it.nomisBookingId == bookingId && it.nomisMovementSeq == sequence }?.dpsCourtMovementId,
    occurredAt = movementTime,
    movementReasonCode = movementReason,
    directionCode = "OUT",
    fromAgencyId = fromPrison,
    toAgencyId = toCourt ?: MISSING_COURT,
    dpsCourtAppearanceScheduleId = oldMappingIds.schedules.find { it.nomisEventId == eventId }?.dpsCourtAppearanceId,
    offenderBookId = bookingId,
    movementSeq = sequence,
    commentText = commentText,
  ),
  created = AtAndBy(audit.createDatetime, audit.createUsername),
  modified = audit.modifyDatetime?.let {
    AtAndBy(
      audit.modifyDatetime,
      audit.modifyUserId!!,
    )
  },
)

private fun BookingCourtMovementIn.toDpsRequest(
  oldMappingIds: CourtSchedulerPrisonerMappingIdsDto,
  bookingId: Long,
  eventId: Long?,
): ResyncCourtEventMovement = ResyncCourtEventMovement(
  movement = CourtEventMovement(
    dpsId = oldMappingIds.movements.find { it.nomisBookingId == bookingId && it.nomisMovementSeq == sequence }?.dpsCourtMovementId,
    occurredAt = movementTime,
    movementReasonCode = movementReason,
    directionCode = "IN",
    fromAgencyId = fromCourt ?: MISSING_COURT,
    toAgencyId = toPrison,
    dpsCourtAppearanceScheduleId = oldMappingIds.schedules.find { it.nomisEventId == eventId }?.dpsCourtAppearanceId,
    offenderBookId = bookingId,
    movementSeq = sequence,
    commentText = commentText,
  ),
  created = AtAndBy(audit.createDatetime, audit.createUsername),
  modified = audit.modifyDatetime?.let {
    AtAndBy(
      audit.modifyDatetime,
      audit.modifyUserId!!,
    )
  },
)

private fun OffenderCourtMovementsResponse.buildMappings(offenderNo: String, migrationId: String, dpsResponse: ResyncResponse) = CourtSchedulerPrisonerMappingsDto(
  offenderNo = offenderNo,
  migrationId = migrationId,
  bookings = bookings.map { booking ->
    CourtSchedulerBookingMappingsDto(
      bookingId = booking.bookingId,
      courtSchedules = booking.courtSchedules.map { schedule ->
        BookingCourtScheduleMappingsDto(
          nomisEventId = schedule.eventId,
          dpsCourtAppearanceId = dpsResponse.findDpsId(schedule.eventId),
          movements = listOfNotNull(
            schedule.courtMovementOut?.buildMapping(booking.bookingId, dpsResponse),
            schedule.courtMovementIn?.buildMapping(booking.bookingId, dpsResponse),
          ),
        )
      },
      unscheduledMovements = booking.unscheduledCourtMovementOuts.map { it.buildUnscheduleMapping(booking.bookingId, dpsResponse) } +
        booking.unscheduledCourtMovementIns.map { it.buildUnscheduledMapping(booking.bookingId, dpsResponse) },
    )
  },
)

private fun BookingCourtMovementOut.buildMapping(bookingId: Long, dpsResponse: ResyncResponse) = BookingCourtMovementMappingsDto(
  nomisMovementSeq = sequence,
  dpsCourtMovementId = dpsResponse.findScheduleMovementDpsId(bookingId, sequence),
)

private fun BookingCourtMovementIn.buildMapping(bookingId: Long, dpsResponse: ResyncResponse) = BookingCourtMovementMappingsDto(
  nomisMovementSeq = sequence,
  dpsCourtMovementId = dpsResponse.findScheduleMovementDpsId(bookingId, sequence),
)

private fun BookingCourtMovementOut.buildUnscheduleMapping(bookingId: Long, dpsResponse: ResyncResponse) = BookingCourtMovementMappingsDto(
  nomisMovementSeq = sequence,
  dpsCourtMovementId = dpsResponse.findUnscheduleMovementDpsId(bookingId, sequence),
)

private fun BookingCourtMovementIn.buildUnscheduledMapping(bookingId: Long, dpsResponse: ResyncResponse) = BookingCourtMovementMappingsDto(
  nomisMovementSeq = sequence,
  dpsCourtMovementId = dpsResponse.findUnscheduleMovementDpsId(bookingId, sequence),
)

private fun ResyncResponse.findDpsId(eventId: Long) = courtEvents.first { mapping -> mapping.eventId == eventId }.dpsId

private fun ResyncResponse.findScheduleMovementDpsId(bookingId: Long, movementSeq: Int) = courtEvents.flatMap { it.movements }.first { mapping -> mapping.offenderBookId == bookingId && mapping.movementSeq == movementSeq }.dpsId

private fun ResyncResponse.findUnscheduleMovementDpsId(bookingId: Long, movementSeq: Int) = unscheduledMovements.first { mapping -> mapping.offenderBookId == bookingId && mapping.movementSeq == movementSeq }.dpsId
