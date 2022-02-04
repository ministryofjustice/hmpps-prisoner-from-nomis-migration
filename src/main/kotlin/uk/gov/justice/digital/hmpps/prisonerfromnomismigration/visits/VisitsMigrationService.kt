package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.generateBatchId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class VisitsMigrationService(
  private val queueService: MigrationQueueService,
  private val nomisApiService: NomisApiService,
  @Value("\${visits.page.size:1000}") private val pageSize: Long
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
      body = migrationFilter,
      estimatedCount = visitCount
    ).apply {
      queueService.sendMessage(MIGRATE_VISITS, this)
    }
  }

  fun migrateVisitsByPage(context: MigrationContext<VisitsMigrationFilter>) {
    (1..context.estimatedCount step pageSize).asSequence()
      .map {
        MigrationContext(
          context = context,
          body = VisitsPage(filter = context.body, pageNumber = it / pageSize, pageSize = pageSize)
        )
      }
      .forEach {
        queueService.sendMessage(MIGRATE_VISITS_BY_PAGE, it)
      }
  }

  fun migrateVisitsForPage(context: MigrationContext<VisitsPage>) {
    log.info("Will calculate visit for page ${context.body.pageNumber} to migrate for migrationId: ${context.migrationId} with filter ${context.body.filter}")
  }
}

data class VisitsPage(val filter: VisitsMigrationFilter, val pageNumber: Long, val pageSize: Long)
