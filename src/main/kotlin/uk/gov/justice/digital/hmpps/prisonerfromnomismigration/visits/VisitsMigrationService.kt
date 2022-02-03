package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.generateBatchId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class VisitsMigrationService(
  private val queueService: MigrationQueueService,
  private val nomisApiService: NomisApiService
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun migrateVisits(migrationFilter: VisitsMigrationFilter): MigrationContext<VisitsMigrationFilter> {
    val visitCount = nomisApiService.getVisits(
      prisonIds = migrationFilter.prisonIds,
      visitTypes = migrationFilter.visitTypes,
      fromDateTime = migrationFilter.fromDateTime,
      toDateTime = migrationFilter.toDateTime,
      pageNumber = 0,
      pageSize = 1,
    ).totalElements

    return MigrationContext(
      migrationId = generateBatchId(),
      filter = migrationFilter,
      estimatedCount = visitCount
    ).apply {
      queueService.sendMessage(MIGRATE_VISITS, this)
    }
  }

  fun migrateVisitsByPage(context: MigrationContext<VisitsMigrationFilter>) =
    log.info("Will calculate visit pages to migrate for migrationId: ${context.migrationId} with filter ${context.filter}")
}
