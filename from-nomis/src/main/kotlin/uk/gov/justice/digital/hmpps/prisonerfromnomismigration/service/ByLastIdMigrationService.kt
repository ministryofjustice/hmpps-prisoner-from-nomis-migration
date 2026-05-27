package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType

abstract class ByLastIdMigrationService<FILTER : Any, NOMIS_ID : Any, MAPPING : Any>(
  mappingService: MigrationMapping<MAPPING>,
  migrationType: MigrationType,
  val pageSize: Long,
  val getIdsParallelCount: Int,
  completeCheckDelaySeconds: Int,
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
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun divideEntitiesByPage(context: MigrationContext<FILTER>) {
    submitNextPageRange(
      pageNumbers = calculatePageNumberRanges(context),
      currentIndex = 0,
      context = context,
      pageSize = pageSize,
      previousEndRangeId = null,
    )
    startStatusCheck(MigrationContext(context = context, body = context.body))
  }

  override suspend fun divideEntitiesByDivision(context: MigrationContext<MigrationDivision<FILTER, NOMIS_ID>>) {
    submitNextPageRange(
      pageNumbers = context.body.pageNumbers,
      currentIndex = context.body.currentPageIndex,
      context = MigrationContext(
        context = context,
        body = context.body.filter,
      ),
      pageSize = context.body.pageSize,
      previousEndRangeId = context.body.previousEndRangeId,
    )
  }

  private fun calculatePageNumberRanges(context: MigrationContext<FILTER>): List<Long> {
    if (getIdsParallelCount < 2 || context.estimatedCount <= pageSize) {
      log.info("Get IDs parallel count of {} too small for the number entities {} so single threaded", getIdsParallelCount, context.estimatedCount)
      return listOf(0)
    }

    val pages = context.estimatedCount / pageSize + 1
    val pageNumbers = (1..getIdsParallelCount).map { page -> page * pages / getIdsParallelCount }
    return pageNumbers
  }

  suspend fun submitNextPageRange(pageNumbers: List<Long>, currentIndex: Int, context: MigrationContext<FILTER>, pageSize: Long, previousEndRangeId: NOMIS_ID?) {
    // when last page this is an open-ended range so no need to find the last range id
    val currentEndRangeId = if (currentIndex == pageNumbers.lastIndex) {
      null
    } else {
      getPageOfIds(context.body, pageSize, pageNumbers[currentIndex]).lastOrNull()
    }
    val currentRange = ByLastId(previousEndRangeId, currentEndRangeId)
    log.info("Sending initial seed range of {}", currentRange)
    queueService.sendMessage(
      MigrationMessageType.MIGRATE_BY_PAGE,
      MigrationContext(
        context = context,
        body = MigrationPage(
          filter = context.body,
          pageKey = currentRange,
          pageSize = pageSize,
        ),
      ),
    )
    if (currentEndRangeId != null) {
      queueService.sendMessage(
        MigrationMessageType.MIGRATE_BY_DIVISION,
        MigrationContext(
          context = context,
          body = MigrationDivision(
            filter = context.body,
            pageNumbers = pageNumbers,
            currentPageIndex = currentIndex + 1,
            pageSize = pageSize,
            previousEndRangeId = currentEndRangeId,
          ),
        ),
      )
    }
  }

  override suspend fun migrateEntitiesForPage(context: MigrationContext<MigrationPage<FILTER, ByLastId<NOMIS_ID>>>) {
    if (migrationHistoryService.isCancelling(context.migrationId)) return

    val pageKey = context.body.pageKey

    // keep only those IDs within our range - filter out those being handled by other threads
    val pageOfIds = getPageOfIdsFromId(pageKey.startRangeId, context.body.filter, pageSize).filter { it <= pageKey.endRangeId }
    if (pageOfIds.isEmpty()) {
      log.info("No more IDs to migrate for page ${pageKey.startRangeId} so shutting down for ${pageKey.endRangeId}")
    } else {
      queueService.sendMessage(
        MigrationMessageType.MIGRATE_BY_PAGE,
        MigrationContext(
          context = context,
          body = MigrationPage(
            filter = context.body.filter,
            pageKey = ByLastId(pageOfIds.last(), pageKey.endRangeId),
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
