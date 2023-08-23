package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.AllocationMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.AllocationMigrateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AllocationMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.FindActiveAllocationIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.GetAllocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class AllocationsMigrationService(
  queueService: MigrationQueueService,
  private val nomisApiService: NomisApiService,
  migrationHistoryService: MigrationHistoryService,
  telemetryClient: TelemetryClient,
  auditService: AuditService,
  private val activitiesApiService: ActivitiesApiService,
  private val allocationsMappingService: AllocationsMappingService,
  private val activityMappingService: ActivitiesMappingService,
  @Value("\${allocations.page.size:50}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : MigrationService<AllocationsMigrationFilter, FindActiveAllocationIdsResponse, GetAllocationResponse, AllocationMigrationMappingDto>(
  queueService = queueService,
  auditService = auditService,
  migrationHistoryService = migrationHistoryService,
  mappingService = allocationsMappingService,
  telemetryClient = telemetryClient,
  migrationType = MigrationType.ALLOCATIONS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {

  private val log = LoggerFactory.getLogger(this::class.java)

  override suspend fun getIds(
    migrationFilter: AllocationsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<FindActiveAllocationIdsResponse> =
    nomisApiService.getAllocationIds(
      prisonId = migrationFilter.prisonId,
      excludeProgramCodes = activitiesApiService.getActivityCategories(),
      courseActivityId = migrationFilter.courseActivityId,
      pageNumber = pageNumber,
      pageSize = pageSize,
    )

  override suspend fun migrateNomisEntity(context: MigrationContext<FindActiveAllocationIdsResponse>) {
    val allocationId = context.body.allocationId
    val migrationId = context.migrationId

    allocationsMappingService.findNomisMapping(allocationId)
      ?.run {
        log.info("Will not migrate the allocationId=$allocationId since it was already mapped to DPS allocationId ${this.activityAllocationId} during migration ${this.label}")
      }
      ?: run {
        nomisApiService.getAllocation(allocationId)
          .let { nomisResponse -> nomisResponse.toAllocationMigrateRequest(allocationId) }
          .let { allocationRequest -> activitiesApiService.migrateAllocation(allocationRequest) }
          .let { allocationResponse -> allocationResponse.toAllocationMigrateMappingDto(allocationId, migrationId) }
          .also { mappingDto -> mappingDto.createAllocationMapping(context) }
          .also { mappingDto -> mappingDto.publishTelemetry() }
      }
  }

  private suspend fun GetAllocationResponse.toAllocationMigrateRequest(allocationId: Long): AllocationMigrateRequest {
    val activityMapping = activityMappingService.findNomisMapping(courseActivityId)
      ?: throw IllegalStateException("Cannot migrate allocation $allocationId - unable to find mapping for course activity $courseActivityId")
    return this.toAllocationMigrateRequest(activityMapping.activityId, activityMapping.activityId2)
  }

  private suspend fun AllocationMigrationMappingDto.createAllocationMapping(context: MigrationContext<*>) =
    try {
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

  private suspend fun AllocationMigrationMappingDto.publishTelemetry() =
    telemetryClient.trackEvent(
      "allocation-migration-entity-migrated",
      mapOf(
        "nomisAllocationId" to nomisAllocationId.toString(),
        "activityAllocationId" to activityAllocationId.toString(),
        "activityId" to activityId.toString(),
        "migrationId" to this.label,
      ),
      null,
    )

  private suspend fun CreateMappingResult<AllocationMigrationMappingDto>.handleError(context: MigrationContext<*>) =
    takeIf { it.isError }
      ?.let { it.errorResponse?.moreInfo }
      ?.also {
        telemetryClient.trackEvent(
          "nomis-migration-allocation-duplicate",
          mapOf(
            "migrationId" to context.migrationId,
            "duplicateNomisAllocationId" to it.duplicate.nomisAllocationId.toString(),
            "duplicateActivityAllocationId" to it.duplicate.activityAllocationId.toString(),
            "duplicateactivityId" to it.duplicate.activityId.toString(),
            "existingNomisAllocationId" to it.existing.nomisAllocationId.toString(),
            "existingActivityAllocationId" to it.existing.activityAllocationId.toString(),
            "existingactivityId" to it.existing.activityId.toString(),
          ),
          null,
        )
      }
}

private fun GetAllocationResponse.toAllocationMigrateRequest(activityId: Long, splitRegimeActivityId: Long?): AllocationMigrateRequest =
  AllocationMigrateRequest(
    prisonCode = prisonId,
    activityId = activityId,
    splitRegimeActivityId = splitRegimeActivityId,
    prisonerNumber = nomisId,
    bookingId = bookingId,
    startDate = startDate,
    endDate = endDate,
    suspendedFlag = suspended,
    endComment = endComment,
    cellLocation = livingUnitDescription,
    nomisPayBand = payBand,
  )

private fun AllocationMigrateResponse.toAllocationMigrateMappingDto(nomisAllocationId: Long, migrationId: String) =
  AllocationMigrationMappingDto(
    nomisAllocationId = nomisAllocationId,
    activityAllocationId = allocationId,
    activityId = activityId,
    label = migrationId,
  )
