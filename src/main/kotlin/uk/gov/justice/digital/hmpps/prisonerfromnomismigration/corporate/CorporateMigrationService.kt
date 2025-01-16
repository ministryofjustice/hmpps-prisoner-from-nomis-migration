package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.corporate

import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorporateMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorporateOrganisationIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType

@Service
class CorporateMigrationService(
  val mappingApiService: CorporateMappingApiService,
  val nomisApiService: CorporateNomisApiService,
  @Value("\${corporate.page.size:1000}") pageSize: Long,
  @Value("\${corporate.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${corporate.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value("\${corporate.complete-check.count}") completeCheckCount: Int,
  @Value("\${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : MigrationService<CorporateMigrationFilter, CorporateOrganisationIdResponse, CorporateMappingsDto>(
  mappingService = mappingApiService,
  migrationType = MigrationType.CORPORATE,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
) {

  override suspend fun getIds(
    migrationFilter: CorporateMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<CorporateOrganisationIdResponse> = nomisApiService.getCorporateOrganisationIdsToMigrate(
    fromDate = migrationFilter.fromDate,
    toDate = migrationFilter.toDate,
    pageNumber = pageNumber,
    pageSize = pageSize,
  )

  override suspend fun migrateNomisEntity(context: MigrationContext<CorporateOrganisationIdResponse>) {
  }

  override suspend fun retryCreateMapping(context: MigrationContext<CorporateMappingsDto>) = createMappingOrOnFailureDo(context, context.body) {
    throw it
  }

  suspend fun createMappingOrOnFailureDo(
    context: MigrationContext<*>,
    mapping: CorporateMappingsDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      mappingApiService.createMappingsForMigration(mapping)
    }.onFailure {
      failureHandler(it)
    }.onSuccess {
      if (it.isError) {
        val duplicateErrorDetails = it.errorResponse!!.moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-corporate-duplicate",
          mapOf(
            "duplicateDpsId" to duplicateErrorDetails.duplicate.dpsId,
            "duplicateNomisId" to duplicateErrorDetails.duplicate.nomisId,
            "existingDpsId" to duplicateErrorDetails.existing.dpsId,
            "existingNomisId" to duplicateErrorDetails.existing.nomisId,
            "migrationId" to context.migrationId,
          ),
        )
      } else {
        telemetryClient.trackEvent(
          "corporate-migration-entity-migrated",
          mapOf(
            "nomisId" to mapping.corporateMapping.nomisId,
            "dpsId" to mapping.corporateMapping.dpsId,
            "migrationId" to context.migrationId,
          ),
        )
      }
    }
  }
}
