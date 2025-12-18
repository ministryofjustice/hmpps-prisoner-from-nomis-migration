package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import kotlin.collections.mapIndexed

@OptIn(ExperimentalCoroutinesApi::class)
abstract class ByLastIdMigrationService<FILTER : Any, NOMIS_ID : Any, MAPPING : Any>(
  mappingService: MigrationMapping<MAPPING>,
  migrationType: MigrationType,
  val pageSize: Long,
  val getIdsParallelCount: Int,
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
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun divideEntitiesByPage(context: MigrationContext<FILTER>) {
    val pageKeys = calculatePageKeyRanges(context)

    log.info("Get IDs parallelism is {} with ranges of {}", pageKeys.size, pageKeys)

    pageKeys.forEach { pageKey ->
      queueService.sendMessage(
        MigrationMessageType.MIGRATE_BY_PAGE,
        MigrationContext<MigrationPage<FILTER, ByLastId<NOMIS_ID>>>(
          context = context,
          body = MigrationPage(
            filter = context.body,
            pageKey = pageKey,
            pageSize = pageSize,
          ),
        ),
      )
    }
    startStatusCheck(MigrationContext(context = context, body = context.body))
  }

  private suspend fun calculatePageKeyRanges(context: MigrationContext<FILTER>): List<ByLastId<NOMIS_ID>> {
    if (getIdsParallelCount < 2 || context.estimatedCount <= pageSize) {
      log.info("Get IDs parallel count of {} too small for the number entities {} so single threaded", getIdsParallelCount, context.estimatedCount)
      return listOf(ByLastId(null, null))
    }

    // calculate page ranges for each parallel request
    val pages = context.estimatedCount / pageSize + 1
    val pageNumbers = (2..getIdsParallelCount).map { page -> (page - 1) * pages / getIdsParallelCount }

    val lastIds = withContext(Dispatchers.Unconfined) {
      pageNumbers
        .map { pageNumber ->
          async {
            getPageOfIds(context.body, pageSize, pageNumber).lastOrNull()
          }
        }
        .awaitAll()
        .filterNotNull()
    }
    val firstPageKey = listOf(ByLastId(null, lastIds.firstOrNull()))
    val pageKeys = firstPageKey + lastIds.mapIndexed { index, id ->
      ByLastId(
        lastId = id,
        endId = lastIds.getOrNull(index + 1),
      )
    }
    return pageKeys
  }

  override suspend fun migrateEntitiesForPage(context: MigrationContext<MigrationPage<FILTER, ByLastId<NOMIS_ID>>>) {
    if (migrationHistoryService.isCancelling(context.migrationId)) return

    val pageKey = context.body.pageKey

    val pageOfIds = getPageOfIdsFromId(pageKey.lastId, context.body.filter, pageSize).filter { it <= pageKey.endId }
    if (pageOfIds.isEmpty()) {
      log.info("No more IDs to migrate for page ${pageKey.lastId} so shutting down for ${pageKey.endId}")
    } else {
      queueService.sendMessage(
        MigrationMessageType.MIGRATE_BY_PAGE,
        MigrationContext(
          context = context,
          body = MigrationPage(
            filter = context.body.filter,
            pageKey = ByLastId(pageOfIds.last(), pageKey.endId),
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
  abstract fun compare(first: NOMIS_ID, second: NOMIS_ID?): Int
  operator fun NOMIS_ID.compareTo(second: NOMIS_ID?) = compare(this, second)
}
