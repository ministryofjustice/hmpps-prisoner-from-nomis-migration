package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitTimeSlotMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType

@Service
class VisitSlotsMigrationService(
  mappingService: VisitSlotsMappingService,
) : MigrationService<Any, Any, VisitTimeSlotMigrationMappingDto>(
  mappingService,
  MigrationType.VISIT_SLOTS,
  pageSize = 1,
  completeCheckDelaySeconds = 1,
  completeCheckCount = 1,
  completeCheckRetrySeconds = 1,
  completeCheckScheduledRetrySeconds = 1,
) {
  override suspend fun getPageOfIds(
    migrationFilter: Any,
    pageSize: Long,
    pageNumber: Long,
  ): List<Any> {
    TODO("Not yet implemented")
  }

  override suspend fun getTotalNumberOfIds(migrationFilter: Any): Long {
    TODO("Not yet implemented")
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<Any>) {
    TODO("Not yet implemented")
  }
}
