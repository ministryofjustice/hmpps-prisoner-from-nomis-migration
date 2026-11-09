package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType

abstract class ByPageNumberMigrationService<FILTER : Any, NOMIS_ID : Any, MAPPING : Any>(
  mappingService: MigrationMapping<MAPPING>,
  migrationType: MigrationType,
  private val pageSize: Long,
  private val completeCheckDelaySeconds: Int,
  completeCheckCount: Int,
  completeCheckRetrySeconds: Int = 1,
  completeCheckScheduledRetrySeconds: Int = completeCheckDelaySeconds,
  jsonMapper: JsonMapper,
) : MigrationService<FILTER, NOMIS_ID, MAPPING, ByPageNumber>(
  mappingService = mappingService,
  migrationType = migrationType,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {
  override suspend fun divideEntitiesByPage(context: MigrationContext<FILTER>) {
    (1..context.estimatedCount step pageSize).asSequence()
      .map {
        MigrationContext(
          context = context,
          body = MigrationPage(filter = context.body, ByPageNumber(pageNumber = it / pageSize), pageSize = pageSize),
        )
      }
      .forEach {
        queueService.sendMessage(MigrationMessageType.MIGRATE_BY_PAGE, it)
      }
    startStatusCheck(context)
  }

  override suspend fun migrateEntitiesForPage(context: MigrationContext<MigrationPage<FILTER, ByPageNumber>>) {
    getPageOfIds(context.body.filter, context.body.pageSize, context.body.pageKey.pageNumber).takeUnless {
      migrationHistoryService.isCancelling(context.migrationId)
    }?.map {
      MigrationContext(
        context = context,
        body = it,
      )
    }?.forEach { queueService.sendMessageNoTracing(MigrationMessageType.MIGRATE_ENTITY, it) }
  }
}
