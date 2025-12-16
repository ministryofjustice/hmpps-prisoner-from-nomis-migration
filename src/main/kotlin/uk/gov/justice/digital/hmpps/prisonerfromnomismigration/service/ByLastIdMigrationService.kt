package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
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
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun divideEntitiesByPage(context: MigrationContext<FILTER>) {
    var currentPageListOfIds: List<NOMIS_ID>? = null
    fun hasFinished(): Boolean = currentPageListOfIds?.isEmpty() == true
    suspend fun hasCancelled(): Boolean = migrationHistoryService.isCancelling(context.migrationId)
    suspend fun shouldContinue() = !hasFinished() && !hasCancelled()

    while (shouldContinue()) {
      log.info("Getting next {} page of ids from ID {}", pageSize, currentPageListOfIds?.lastOrNull())
      tryGetPageOfIdsFromId(currentPageListOfIds?.lastOrNull(), context.body, pageSize)
        .onSuccess { pageListOfIds ->
          currentPageListOfIds = pageListOfIds
          currentPageListOfIds.map { nomisId ->
            MigrationContext(
              context = context,
              body = nomisId,
            )
          }.forEach { nomisId -> queueService.sendMessageNoTracing(MigrationMessageType.MIGRATE_ENTITY, nomisId) }
        }
        .onFailure { error ->
          telemetryClient.trackEvent(
            "${migrationType.telemetryName}-migration-page-failed",
            mapOf(
              "migrationId" to context.migrationId,
              "failureLastId" to (currentPageListOfIds?.lastOrNull()?.toString() ?: "NOT_STARTED"),
              "reason" to (error.message ?: error.stackTraceToString()),
            ),
          )
        }
    }

    startStatusCheck(context)
  }

  suspend fun tryGetPageOfIdsFromId(lastId: NOMIS_ID?, migrationFilter: FILTER, pageSize: Long): Result<List<NOMIS_ID>> = kotlin.runCatching { getPageOfIdsFromId(lastId, migrationFilter, pageSize) }
  abstract suspend fun getPageOfIdsFromId(lastId: NOMIS_ID?, migrationFilter: FILTER, pageSize: Long): List<NOMIS_ID>
  override suspend fun getPageOfIds(migrationFilter: FILTER, pageSize: Long, pageNumber: Long): List<NOMIS_ID> = throw IllegalStateException("Should not be called for this migration type")
}
