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
) : MigrationService<FILTER, NOMIS_ID, MAPPING>(
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
      MigrationContext<MigrationPage<FILTER>>(
        context = context,
        body = MigrationPage(
          filter = context.body,
          pageKey = ByLastId(null),
          pageSize = pageSize,
        ),
      ),
    )
  }

  override suspend fun migrateEntitiesForPage(context: MigrationContext<MigrationPage<FILTER>>) {
    if (migrationHistoryService.isCancelling(context.migrationId)) return

    when (context.body.pageKey) {
      is ByLastId<*> -> {
        // not sure how to avoid this cast without the PageKey being typed with NOMIS_ID that makes no sense so for now we
        // know this must work and cannot get a case class exception
        @Suppress("UNCHECKED_CAST")
        val pageKey = context.body.pageKey as ByLastId<NOMIS_ID>

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

      is ByPageNumber -> throw IllegalStateException("Should not be called for this migration type")
    }
  }

  abstract suspend fun getPageOfIdsFromId(lastId: NOMIS_ID?, migrationFilter: FILTER, pageSize: Long): List<NOMIS_ID>
  override suspend fun getPageOfIds(migrationFilter: FILTER, pageSize: Long, pageNumber: Long): List<NOMIS_ID> = throw IllegalStateException("Should not be called for this migration type")
}
