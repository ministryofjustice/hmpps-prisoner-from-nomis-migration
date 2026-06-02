package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.ActivityMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.ActivityMigrateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.NomisPayRate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.NomisScheduleRule
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.BadRequestException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ActivityMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.GetActivityResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PayRatesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduleRulesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumberMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.ACTIVITIES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NotFoundException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*
import kotlin.math.max

@Service
class ActivitiesMigrationService(
  private val nomisApiService: NomisApiService,
  private val activitiesApiService: ActivitiesApiService,
  private val activitiesMappingService: ActivitiesMappingService,
  jsonMapper: JsonMapper,
  @Value("\${activities.page.size:20}") pageSize: Long,
  @Value("\${activities.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${activities.complete-check.count}") completeCheckCount: Int,
  @Value("\${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : ByPageNumberMigrationService<ActivitiesMigrationFilter, ActivitiesMigrationRequest, ActivityMigrationMappingDto>(
  mappingService = activitiesMappingService,
  migrationType = ACTIVITIES,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {

  private val log = LoggerFactory.getLogger(this::class.java)

  suspend fun getIds(
    migrationFilter: ActivitiesMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<ActivitiesMigrationRequest> {
    val start = migrationFilter.activityStartDate ?: LocalDate.now().plusDays(1)
    return nomisApiService.getActivityIds(
      prisonId = migrationFilter.prisonId,
      courseActivityId = migrationFilter.courseActivityId,
      pageNumber = pageNumber,
      pageSize = pageSize,
    ).map { ActivitiesMigrationRequest(it.courseActivityId, start, it.hasScheduleRules) } as PageImpl<ActivitiesMigrationRequest>
  }

  override suspend fun getPageOfIds(
    migrationFilter: ActivitiesMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): List<ActivitiesMigrationRequest> = getIds(migrationFilter, pageSize, pageNumber).content

  override suspend fun getTotalNumberOfIds(migrationFilter: ActivitiesMigrationFilter): Long = getIds(migrationFilter, 1, 0).totalElements

  override suspend fun migrateNomisEntity(context: MigrationContext<ActivitiesMigrationRequest>) {
    val courseActivityId = context.body.courseActivityId
    val requestedStartDate = context.body.activityStartDate
    val migrationId = context.migrationId

    activitiesMappingService.findNomisMapping(courseActivityId)
      ?.run {
        log.info("Will not migrate the courseActivityId=$courseActivityId since it was already mapped to activityIds ${this.activityId} and ${this.activityId2} during migration ${this.label}")
        telemetryClient.trackEvent(
          "${ACTIVITIES.telemetryName}-migration-entity-ignored",
          mapOf("nomisCourseActivityId" to courseActivityId.toString(), "migrationId" to migrationId, "reason" to "Mapping already exists - must have already been migrated"),
        )
      }
      ?: runCatching {
        // Only migrate if there are schedule rules, but create a mapping either way so we remember to end the NOMIS activity regardless
        val activityMappingDto = if (context.body.hasScheduleRules) {
          nomisApiService.getActivity(courseActivityId)
            .toActivityMigrateRequest(requestedStartDate)
            .let { activitiesRequest -> activitiesApiService.migrateActivity(activitiesRequest) }
            .toActivityMigrateMappingDto(courseActivityId, migrationId)
        } else {
          ActivityMigrationMappingDto(courseActivityId, migrationId, null, null)
        }
        activityMappingDto.createActivityMapping(context)
        activityMappingDto.publishTelemetry()
      }
        .onFailure {
          telemetryClient.trackEvent(
            "${ACTIVITIES.telemetryName}-migration-entity-failed",
            mapOf("nomisCourseActivityId" to courseActivityId.toString(), "reason" to it.toString(), "migrationId" to migrationId),
          )
          throw it
        }
  }

  suspend fun endMigratedActivities(migrationId: String) {
    val activityCount = activitiesMappingService.countMigrationMappings(migrationId, includeIgnored = true)
    if (activityCount == 0L) throw NotFoundException("No migrations found for $migrationId")

    val allActivityIds = activitiesMappingService.getActivityMigrationDetails(migrationId, activityCount).content
      .map { it.nomisCourseActivityId }

    val migration = migrationHistoryService.get(migrationId)
    val filter = jsonMapper.readValue(migration.filter, ActivitiesMigrationFilter::class.java)
    // There will always be a start date because it's now mandatory in the UI, but is still nullable due to old data that can be displayed
    filter.nomisActivityEndDate = filter.activityStartDate!!.minusDays(1)
    nomisApiService.endActivities(allActivityIds, filter.nomisActivityEndDate!!)
    migrationHistoryService.updateFilter(migrationId, filter)
  }

  suspend fun moveActivityStartDates(migrationId: String, newActivityStartDate: LocalDate): List<String> {
    val activityCount = activitiesMappingService.countMigrationMappings(migrationId, includeIgnored = true)
    if (activityCount == 0L) throw NotFoundException("No migrations found for $migrationId")

    val allActivityIds = activitiesMappingService.getActivityMigrationDetails(migrationId, activityCount).content
      .map { it.nomisCourseActivityId }

    val filter = migrationHistoryService.get(migrationId)
      .let { jsonMapper.readValue(it.filter, ActivitiesMigrationFilter::class.java) }

    if (newActivityStartDate <= filter.activityStartDate) throw BadRequestException("The new start date must be after the current start date ${filter.activityStartDate}")

    val oldNomisEndDate = filter.nomisActivityEndDate ?: throw BadRequestException("Can only move start dates if the NOMIS activities have been ended")
    val newNomisEndDate = newActivityStartDate.minusDays(1)

    return try {
      nomisApiService.moveActivityEndDates(allActivityIds, oldNomisEndDate, newNomisEndDate)
        .also { filter.nomisActivityEndDate = newNomisEndDate }
      activitiesApiService.moveActivityStartDates(filter.prisonId, newActivityStartDate)
        .also { filter.activityStartDate = newActivityStartDate }
    } catch (e: Exception) {
      throw MoveActivityStartDatesException(e)
    } finally {
      migrationHistoryService.updateFilter(migrationId, filter)
    }
  }

  private suspend fun ActivityMigrationMappingDto.createActivityMapping(context: MigrationContext<*>) = try {
    activitiesMappingService.createMapping(this, object : ParameterizedTypeReference<DuplicateErrorResponse<ActivityMigrationMappingDto>>() {})
      .also { it.handleError(context) }
  } catch (e: Exception) {
    log.error(
      "Failed to create activity mapping for nomisCourseActivityId: $nomisCourseActivityId, activityIds $activityId and $activityId2 for migration ${this.label}",
      e,
    )
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = this,
      ),
    )
  }

  private suspend fun ActivityMigrationMappingDto.publishTelemetry() = telemetryClient.trackEvent(
    "${ACTIVITIES.telemetryName}-migration-entity-${activityId?.let { "migrated" } ?: "ignored"}",
    mutableMapOf(
      "nomisCourseActivityId" to nomisCourseActivityId.toString(),
      "dpsActivityId" to activityId?.toString(),
      "dpsActivityId2" to activityId2?.toString(),
      "migrationId" to this.label,
    ).apply {
      if (activityId == null) put("reason", "No schedule rules exist for activity")
    }.toMap(),
    null,
  )

  private suspend fun CreateMappingResult<ActivityMigrationMappingDto>.handleError(context: MigrationContext<*>) = takeIf { it.isError }
    ?.let { it.errorResponse?.moreInfo }
    ?.also {
      telemetryClient.trackEvent(
        "${ACTIVITIES.telemetryName}-nomis-migration-duplicate",
        mapOf(
          "migrationId" to context.migrationId,
          "duplicateNomisCourseActivityId" to it.duplicate.nomisCourseActivityId.toString(),
          "duplicateActivityId" to it.duplicate.activityId.toString(),
          "duplicateActivityId2" to it.duplicate.activityId2.toString(),
          "existingNomisCourseActivityId" to it.existing.nomisCourseActivityId.toString(),
          "existingActivityId" to it.existing.activityId.toString(),
          "existingActivityId2" to it.existing.activityId2.toString(),
        ),
        null,
      )
    }

  private suspend fun GetActivityResponse.toActivityMigrateRequest(requestedStartDate: LocalDate): ActivityMigrateRequest = ActivityMigrateRequest(
    programServiceCode = programCode,
    prisonCode = prisonId,
    startDate = maxOf(startDate, requestedStartDate),
    endDate = endDate,
    capacity = max(1, capacity),
    description = description,
    payPerSession = ActivityMigrateRequest.PayPerSession.valueOf(payPerSession),
    runsOnBankHoliday = !excludeBankHolidays,
    dpsLocationId = internalLocationId.toDpsLocationId(),
    scheduleRules = scheduleRules.toNomisScheduleRules(),
    payRates = payRates.map { it.toNomisPayRate() },
    outsideWork = outsideWork,
  )

  private suspend fun Long?.toDpsLocationId(): UUID? = this?.let { activitiesMappingService.getDpsLocation(it).dpsLocationId }?.let { UUID.fromString(it) }
  override fun parseContextFilter(json: String): MigrationMessage<*, ActivitiesMigrationFilter> = jsonMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<ActivitiesMigrationFilter, ByPageNumber>> = jsonMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, ActivitiesMigrationRequest> = jsonMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, ActivityMigrationMappingDto> = jsonMapper.readValue(json)
}

private fun List<ScheduleRulesResponse>.toNomisScheduleRules(): List<NomisScheduleRule> = map {
  NomisScheduleRule(
    startTime = it.startTime,
    endTime = it.endTime,
    monday = it.monday,
    tuesday = it.tuesday,
    wednesday = it.wednesday,
    thursday = it.thursday,
    friday = it.friday,
    saturday = it.saturday,
    sunday = it.sunday,
    timeSlot = it.slot().value.let { slotValue -> NomisScheduleRule.TimeSlot.valueOf(slotValue) },
  )
}.distinct()

private fun PayRatesResponse.toNomisPayRate(): NomisPayRate = NomisPayRate(
  incentiveLevel = incentiveLevelCode,
  rate = rate.multiply(BigDecimal.valueOf(100)).toInt(),
  nomisPayBand = payBand,
)

private fun ActivityMigrateResponse.toActivityMigrateMappingDto(courseActivityId: Long, migrationId: String) = ActivityMigrationMappingDto(
  nomisCourseActivityId = courseActivityId,
  activityId = activityId,
  activityId2 = splitRegimeActivityId,
  label = migrationId,
)

class MoveActivityStartDatesException(cause: Throwable? = null) : RuntimeException("Failed to move activity start dates", cause)
