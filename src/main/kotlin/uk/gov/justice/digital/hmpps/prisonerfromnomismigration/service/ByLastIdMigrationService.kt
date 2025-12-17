package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import kotlin.collections.forEach

abstract class ByLastIdMigrationService<FILTER : Any, NOMIS_ID : Any, MAPPING : Any>(
  mappingService: MigrationMapping<MAPPING>,
  migrationType: MigrationType,
  val pageSize: Long,
  completeCheckDelaySeconds: Int,
  completeCheckCount: Int,
  completeCheckRetrySeconds: Int = 1,
  completeCheckScheduledRetrySeconds: Int = completeCheckDelaySeconds,
) : MigrationService<FILTER, NOMIS_ID, MAPPING, ByLastId<NOMIS_ID>>(
  mappingService = mappingService,
  migrationType = migrationType,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
) {
  override suspend fun divideEntitiesByPage(context: MigrationContext<FILTER>) {
    // start by getting the first page of results
    queueService.sendMessage(
      MigrationMessageType.MIGRATE_BY_PAGE,
      MigrationContext<MigrationPage<FILTER, ByLastId<NOMIS_ID>>>(
        context = context,
        body = MigrationPage(
          filter = context.body,
          pageKey = ByLastId(null),
          pageSize = pageSize,
        ),
      ),
    )
  }

  override suspend fun migrateEntitiesForPage(context: MigrationContext<MigrationPage<FILTER, ByLastId<NOMIS_ID>>>) {
    if (migrationHistoryService.isCancelling(context.migrationId)) return

    val pageKey = context.body.pageKey

    val pageOfIds = getPageOfIdsFromId(pageKey.lastId, context.body.filter, pageSize)
    if (pageOfIds.isEmpty()) {
      // we are done - so start shutting down
      startStatusCheck(MigrationContext(context = context, body = context.body.filter))
    } else {
      // request next page and then migrate this page
      queueService.sendMessage(
        MigrationMessageType.MIGRATE_BY_PAGE,
        MigrationContext(
          context = context,
          body = MigrationPage(
            filter = context.body.filter,
            pageKey = ByLastId(pageOfIds.last()),
            pageSize = context.body.pageSize,
          ),
        ),
      )
      pageOfIds.map {
        MigrationContext(
          context = context,
          body = it,
        )
      }.forEach { queueService.sendMessageNoTracing(MigrationMessageType.MIGRATE_ENTITY, it) }
    }
  }

  abstract suspend fun getPageOfIdsFromId(lastId: NOMIS_ID?, migrationFilter: FILTER, pageSize: Long): List<NOMIS_ID>
  override suspend fun getPageOfIds(migrationFilter: FILTER, pageSize: Long, pageNumber: Long): List<NOMIS_ID> = throw IllegalStateException("Should not be called for this migration type")
}
