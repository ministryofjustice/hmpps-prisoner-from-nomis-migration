package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.ActivityMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.ActivityMigrateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.NomisPayRate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.NomisScheduleRule
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ActivityMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.FindActiveActivityIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.GetActivityResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PayRatesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ScheduleRulesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.ACTIVITIES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import java.math.BigDecimal

@Service
class ActivitiesMigrationService(
  queueService: MigrationQueueService,
  private val nomisApiService: NomisApiService,
  migrationHistoryService: MigrationHistoryService,
  telemetryClient: TelemetryClient,
  auditService: AuditService,
  private val activitiesApiService: ActivitiesApiService,
  private val activitiesMappingService: ActivitiesMappingService,
  @Value("\${activities.page.size:20}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : MigrationService<ActivitiesMigrationFilter, FindActiveActivityIdsResponse, GetActivityResponse, ActivityMigrationMappingDto>(
  queueService = queueService,
  auditService = auditService,
  migrationHistoryService = migrationHistoryService,
  mappingService = activitiesMappingService,
  telemetryClient = telemetryClient,
  migrationType = ACTIVITIES,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {

  private val log = LoggerFactory.getLogger(this::class.java)

  override suspend fun getIds(
    migrationFilter: ActivitiesMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<FindActiveActivityIdsResponse> =
    nomisApiService.getActivityIds(
      prisonId = migrationFilter.prisonId,
      excludeProgramCodes = activitiesApiService.getActivityCategories(),
      courseActivityId = migrationFilter.courseActivityId,
      pageNumber = pageNumber,
      pageSize = pageSize,
    )

  override suspend fun migrateNomisEntity(context: MigrationContext<FindActiveActivityIdsResponse>) {
    val courseActivityId = context.body.courseActivityId
    val migrationId = context.migrationId

    activitiesMappingService.findNomisMapping(courseActivityId)
      ?.run {
        log.info("Will not migrate the courseActivityId=$courseActivityId since it was already mapped to activityIds ${this.activityId} and ${this.activityId2} during migration ${this.label}")
      }
      ?: run {
        nomisApiService.getActivity(courseActivityId)
          .let { nomisResponse -> nomisResponse.toActivityMigrateRequest() }
          .let { activitiesRequest -> activitiesApiService.migrateActivity(activitiesRequest) }
          .let { activitiesResponse -> activitiesResponse.toActivityMigrateMappingDto(courseActivityId, migrationId) }
          .also { mappingDto -> mappingDto.createActivityMapping(context) }
          .also { mappingDto -> mappingDto.publishTelemetry() }
      }
  }

  private suspend fun ActivityMigrationMappingDto.createActivityMapping(context: MigrationContext<*>) =
    try {
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

  private suspend fun ActivityMigrationMappingDto.publishTelemetry() =
    telemetryClient.trackEvent(
      "${ACTIVITIES.telemetryName}-migration-entity-migrated",
      mapOf(
        "nomisCourseActivityId" to nomisCourseActivityId.toString(),
        "activityId" to activityId.toString(),
        "activityId2" to activityId2?.toString(),
        "migrationId" to this.label,
      ),
      null,
    )

  private suspend fun CreateMappingResult<ActivityMigrationMappingDto>.handleError(context: MigrationContext<*>) =
    takeIf { it.isError }
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
}

private fun GetActivityResponse.toActivityMigrateRequest(): ActivityMigrateRequest =
  ActivityMigrateRequest(
    programServiceCode = programCode,
    prisonCode = prisonId,
    startDate = startDate,
    endDate = endDate,
    capacity = capacity,
    description = description,
    payPerSession = ActivityMigrateRequest.PayPerSession.valueOf(payPerSession),
    minimumIncentiveLevel = minimumIncentiveLevel,
    runsOnBankHoliday = !excludeBankHolidays,
    internalLocationCode = internalLocationCode,
    internalLocationId = internalLocationId,
    internalLocationDescription = internalLocationDescription,
    scheduleRules = scheduleRules.map { it.toNomisScheduleRule() },
    payRates = payRates.map { it.toNomisPayRate() },
    outsideWork = outsideWork,
  )

private fun ScheduleRulesResponse.toNomisScheduleRule(): NomisScheduleRule =
  NomisScheduleRule(
    startTime = startTime,
    endTime = endTime,
    monday = monday,
    tuesday = tuesday,
    wednesday = wednesday,
    thursday = thursday,
    friday = friday,
    saturday = saturday,
    sunday = sunday,
  )

private fun PayRatesResponse.toNomisPayRate(): NomisPayRate =
  NomisPayRate(
    incentiveLevel = incentiveLevelCode,
    rate = rate.multiply(BigDecimal.valueOf(100)).toInt(),
    nomisPayBand = payBand,
  )

private fun ActivityMigrateResponse.toActivityMigrateMappingDto(courseActivityId: Long, migrationId: String) =
  ActivityMigrationMappingDto(
    nomisCourseActivityId = courseActivityId,
    activityId = activityId,
    activityId2 = splitRegimeActivityId,
    label = migrationId,
  )
