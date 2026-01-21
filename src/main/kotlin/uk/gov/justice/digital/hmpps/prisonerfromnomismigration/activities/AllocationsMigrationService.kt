package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.AllocationMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.AllocationMigrateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot.DaysOfWeek.FRIDAY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot.DaysOfWeek.MONDAY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot.DaysOfWeek.SATURDAY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot.DaysOfWeek.SUNDAY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot.DaysOfWeek.THURSDAY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot.DaysOfWeek.TUESDAY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot.DaysOfWeek.WEDNESDAY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot.TimeSlot.AM
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot.TimeSlot.ED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot.TimeSlot.PM
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AllocationMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AllocationExclusion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.FindActiveAllocationIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.GetAllocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ScheduleRulesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumberMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import java.time.LocalTime

@Service
class AllocationsMigrationService(
  private val nomisApiService: NomisApiService,
  private val activitiesApiService: ActivitiesApiService,
  private val allocationsMappingService: AllocationsMappingService,
  private val activityMappingService: ActivitiesMappingService,
  jsonMapper: JsonMapper,
  @Value("\${allocations.page.size:50}") pageSize: Long,
  @Value("\${allocations.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${allocations.complete-check.count}") completeCheckCount: Int,
  @Value("\${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : ByPageNumberMigrationService<AllocationsMigrationFilter, FindActiveAllocationIdsResponse, AllocationMigrationMappingDto>(
  mappingService = allocationsMappingService,
  migrationType = MigrationType.ALLOCATIONS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {

  private val log = LoggerFactory.getLogger(this::class.java)

  suspend fun getIds(
    migrationFilter: AllocationsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<FindActiveAllocationIdsResponse> = nomisApiService.getAllocationIds(
    prisonId = migrationFilter.prisonId,
    courseActivityId = migrationFilter.courseActivityId,
    activeOnDate = migrationFilter.activityStartDate,
    pageNumber = pageNumber,
    pageSize = pageSize,
  )

  override suspend fun getPageOfIds(
    migrationFilter: AllocationsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): List<FindActiveAllocationIdsResponse> = getIds(migrationFilter, pageSize, pageNumber).content

  override suspend fun getTotalNumberOfIds(migrationFilter: AllocationsMigrationFilter): Long = getIds(migrationFilter, 1, 0).totalElements

  override suspend fun migrateNomisEntity(context: MigrationContext<FindActiveAllocationIdsResponse>) {
    val allocationId = context.body.allocationId
    val migrationId = context.migrationId

    allocationsMappingService.findNomisMapping(allocationId)
      ?.run {
        log.info("Will not migrate the allocationId=$allocationId since it was already mapped to DPS allocationId ${this.activityAllocationId} during migration ${this.label}")
        telemetryClient.trackEvent(
          "${MigrationType.ALLOCATIONS.telemetryName}-migration-entity-ignored",
          mapOf("nomisAllocationId" to allocationId.toString(), "migrationId" to migrationId),
        )
      }
      ?: runCatching {
        nomisApiService.getAllocation(allocationId)
          .let { nomisResponse -> nomisResponse.toAllocationMigrateRequest(allocationId) }
          .let { allocationRequest -> activitiesApiService.migrateAllocation(allocationRequest) }
          .let { allocationResponse -> allocationResponse.toAllocationMigrateMappingDto(allocationId, migrationId) }
          .also { mappingDto -> mappingDto.createAllocationMapping(context) }
          .also { mappingDto -> mappingDto.publishTelemetry() }
      }
        .onFailure {
          telemetryClient.trackEvent(
            "${MigrationType.ALLOCATIONS.telemetryName}-migration-entity-failed",
            mapOf("nomisAllocationId" to allocationId.toString(), "reason" to it.toString(), "migrationId" to migrationId),
          )
          throw it
        }
  }

  private suspend fun GetAllocationResponse.toAllocationMigrateRequest(allocationId: Long): AllocationMigrateRequest {
    val activityMapping = activityMappingService.findNomisMapping(courseActivityId)
      ?: throw IllegalStateException("Cannot migrate allocation $allocationId - unable to find mapping for course activity $courseActivityId")
    return this.toAllocationMigrateRequest(activityMapping.activityId!!, activityMapping.activityId2)
  }

  private suspend fun AllocationMigrationMappingDto.createAllocationMapping(context: MigrationContext<*>) = try {
    allocationsMappingService.createMapping(this, object : ParameterizedTypeReference<DuplicateErrorResponse<AllocationMigrationMappingDto>>() {})
      .also { it.handleError(context) }
  } catch (e: Exception) {
    log.error(
      "Failed to create activity mapping for nomisAllocationId: $nomisAllocationId, DPS allocation ID $activityAllocationId for migration ${this.label}",
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

  private suspend fun AllocationMigrationMappingDto.publishTelemetry() = telemetryClient.trackEvent(
    "${MigrationType.ALLOCATIONS.telemetryName}-migration-entity-migrated",
    mapOf(
      "nomisAllocationId" to nomisAllocationId.toString(),
      "dpsAllocationId" to activityAllocationId.toString(),
      "dpsActivityId" to activityId.toString(),
      "migrationId" to this.label,
    ),
    null,
  )

  private suspend fun CreateMappingResult<AllocationMigrationMappingDto>.handleError(context: MigrationContext<*>) = takeIf { it.isError }
    ?.let { it.errorResponse?.moreInfo }
    ?.also {
      telemetryClient.trackEvent(
        "${MigrationType.ALLOCATIONS.telemetryName}-nomis-migration-duplicate",
        mapOf(
          "migrationId" to context.migrationId,
          "duplicateNomisAllocationId" to it.duplicate.nomisAllocationId.toString(),
          "duplicateDpsAllocationId" to it.duplicate.activityAllocationId.toString(),
          "duplicateactivityId" to it.duplicate.activityId.toString(),
          "existingNomisAllocationId" to it.existing.nomisAllocationId.toString(),
          "existingDpsAllocationId" to it.existing.activityAllocationId.toString(),
          "existingactivityId" to it.existing.activityId.toString(),
        ),
        null,
      )
    }
  override fun parseContextFilter(json: String): MigrationMessage<*, AllocationsMigrationFilter> = jsonMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<AllocationsMigrationFilter, ByPageNumber>> = jsonMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, FindActiveAllocationIdsResponse> = jsonMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, AllocationMigrationMappingDto> = jsonMapper.readValue(json)
}

private fun GetAllocationResponse.toAllocationMigrateRequest(activityId: Long, splitRegimeActivityId: Long?): AllocationMigrateRequest = AllocationMigrateRequest(
  prisonCode = prisonId,
  activityId = activityId,
  splitRegimeActivityId = splitRegimeActivityId,
  prisonerNumber = nomisId,
  bookingId = bookingId,
  startDate = maxOf(startDate, activityStartDate),
  endDate = endDate,
  suspendedFlag = suspended,
  endComment = endComment,
  cellLocation = livingUnitDescription,
  nomisPayBand = payBand,
  exclusions = exclusions.toDpsExclusions(scheduleRules),
)

fun List<AllocationExclusion>.toDpsExclusions(scheduleRules: List<ScheduleRulesResponse>): List<Slot> = listOf(AM, PM, ED).map { timeSlot ->
  this
    .filter { exclusion ->
      val slot = exclusion.slot
      slot == null || slot.value == timeSlot.value
    }
    .map { exclusion -> exclusion.findDay() }
    .filter { day -> scheduleRules.includes(day, timeSlot) }
    .toSet()
    .let { daysOfWeek ->
      Slot(
        weekNumber = 1,
        timeSlot = timeSlot,
        monday = MONDAY in daysOfWeek,
        tuesday = TUESDAY in daysOfWeek,
        wednesday = WEDNESDAY in daysOfWeek,
        thursday = THURSDAY in daysOfWeek,
        friday = FRIDAY in daysOfWeek,
        saturday = SATURDAY in daysOfWeek,
        sunday = SUNDAY in daysOfWeek,
        daysOfWeek = daysOfWeek,
      )
    }
}.filter { it.daysOfWeek.isNotEmpty() }

private fun AllocationExclusion.findDay() = Slot.DaysOfWeek.entries.first { day -> day.value.startsWith(this.day.value) }

private fun AllocationMigrateResponse.toAllocationMigrateMappingDto(nomisAllocationId: Long, migrationId: String) = AllocationMigrationMappingDto(
  nomisAllocationId = nomisAllocationId,
  activityAllocationId = allocationId,
  activityId = activityId,
  label = migrationId,
)

private fun List<ScheduleRulesResponse>.includes(day: Slot.DaysOfWeek, slot: Slot.TimeSlot) = any { scheduleRule ->
  scheduleRule.slot().value == slot.value &&
    when (day) {
      MONDAY -> scheduleRule.monday
      TUESDAY -> scheduleRule.tuesday
      WEDNESDAY -> scheduleRule.wednesday
      THURSDAY -> scheduleRule.thursday
      FRIDAY -> scheduleRule.friday
      SATURDAY -> scheduleRule.saturday
      SUNDAY -> scheduleRule.sunday
    }
}

fun ScheduleRulesResponse.slot(): Slot.TimeSlot = LocalTime.parse(startTime).let {
  when {
    it.hour < 12 -> AM
    it.hour < 17 -> PM
    else -> ED
  }
}
