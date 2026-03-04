package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import kotlin.collections.forEach
import kotlin.collections.map

abstract class ByIdRangeMigrationService<FILTER : Any, NOMIS_ID : Any, MAPPING : Any>(
  mappingService: MigrationMapping<MAPPING>,
  migrationType: MigrationType,
  private val pageSize: Long,
  private val completeCheckDelaySeconds: Int,
  completeCheckCount: Int,
  completeCheckRetrySeconds: Int = 1,
  completeCheckScheduledRetrySeconds: Int = completeCheckDelaySeconds,
  jsonMapper: JsonMapper,
) : MigrationService<FILTER, NOMIS_ID, MAPPING, ByLastId<NOMIS_ID>>(
  mappingService = mappingService,
  migrationType = migrationType,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {
  override suspend fun divideEntitiesByPage(context: MigrationContext<FILTER>) {
    getRangeOfIds(context.body, pageSize)
      .map {
        MigrationContext(
          context = context,
          body = MigrationPage(filter = context.body, ByLastId(it.first, it.second), pageSize = pageSize),
        )
      }
      .forEach {
        queueService.sendMessage(MigrationMessageType.MIGRATE_BY_PAGE, it)
      }
    startStatusCheck(context)
  }

  override suspend fun migrateEntitiesForPage(context: MigrationContext<MigrationPage<FILTER, ByLastId<NOMIS_ID>>>) {
    if (migrationHistoryService.isCancelling(context.migrationId)) return

    val pageKey = context.body.pageKey

    getPageOfIdsFromIdRange(pageKey.startRangeId, pageKey.endRangeId, context.body.filter).map {
      MigrationContext(context = context, body = it)
    }.forEach { queueService.sendMessageNoTracing(MigrationMessageType.MIGRATE_ENTITY, it) }
  }

  abstract suspend fun getRangeOfIds(body: FILTER, pageSize: Long): List<Pair<NOMIS_ID, NOMIS_ID>>
  abstract suspend fun getPageOfIdsFromIdRange(firstId: NOMIS_ID?, lastId: NOMIS_ID?, migrationFilter: FILTER): List<NOMIS_ID>

  override suspend fun getPageOfIds(
    migrationFilter: FILTER,
    pageSize: Long,
    pageNumber: Long,
  ): List<NOMIS_ID> = throw kotlin.IllegalStateException("Not valid for ByRangeId migrations")
}
