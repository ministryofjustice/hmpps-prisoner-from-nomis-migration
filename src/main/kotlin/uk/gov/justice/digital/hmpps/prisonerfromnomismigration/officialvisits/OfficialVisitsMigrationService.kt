package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType

@Service
class OfficialVisitsMigrationService(
  mappingService: OfficialVisitsMappingService,
) : MigrationService<Any, Any, Any>(
  mappingService,
  MigrationType.OFFICIAL_VISITS,
  pageSize = 1,
  completeCheckDelaySeconds = 1,
  completeCheckCount = 1,
  completeCheckRetrySeconds = 1,
  completeCheckScheduledRetrySeconds = 1,
) {
  override suspend fun getIds(
    migrationFilter: Any,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<Any> {
    TODO("Not yet implemented")
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<Any>) {
    TODO("Not yet implemented")
  }
}
