package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType

/**
 * A base class for the simplest kind of migration - one with no FILTER, no pagination and no
 * MAPPING. All ids are retrieved in one go (see [getIds]) and each is migrated directly via
 * [migrateNomisEntity]
 *
 * This is suitable for very simple migrations where the volume of data is small enough that
 * paging isn't required and there is no need to record a NOMIS id to DPS id mapping, e.g. where
 * the migration is idempotent or the DPS id is deterministic from the NOMIS id.
 */
abstract class SimpleMigrationService<NOMIS_ID : Any>(
  mappingApiWebClient: WebClient,
  migrationType: MigrationType,
  completeCheckDelaySeconds: Int,
  completeCheckCount: Int,
  completeCheckRetrySeconds: Int = 1,
  completeCheckScheduledRetrySeconds: Int = completeCheckDelaySeconds,
  jsonMapper: JsonMapper,
) : MigrationService<Any, NOMIS_ID, Any, ByPageNumber>(
  // no mapping api is used for this type of migration - placeholder implementation to satisfy the base class
  mappingService = object : MigrationMapping<Any>(domainUrl = "", webClient = mappingApiWebClient) {},
  migrationType = migrationType,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {

  // there is no filter for this type of migration so all ids are always returned
  abstract suspend fun getIds(): List<NOMIS_ID>

  final override suspend fun getPageOfIds(migrationFilter: Any, pageSize: Long, pageNumber: Long): List<NOMIS_ID> = getIds()

  final override suspend fun getTotalNumberOfIds(migrationFilter: Any): Long = getIds().size.toLong()

  // there is no pagination for this type of migration so every id is migrated in one go rather than being
  // split across MIGRATE_BY_PAGE messages
  final override suspend fun divideEntitiesByPage(context: MigrationContext<Any>) {
    getIds().takeUnless { migrationHistoryService.isCancelling(context.migrationId) }
      ?.forEach { id ->
        queueService.sendMessageNoTracing(
          MigrationMessageType.MIGRATE_ENTITY,
          MigrationContext(context = context, body = id),
        )
      }
    startStatusCheck(context)
  }

  final override suspend fun migrateEntitiesForPage(context: MigrationContext<MigrationPage<Any, ByPageNumber>>): Unit = throw NotImplementedError("Paging is not supported for a SimpleMigrationService - all entities are migrated directly by divideEntitiesByPage")

  final override fun parseContextFilter(json: String): MigrationMessage<*, Any> = jsonMapper.readValue(json)

  final override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<Any, ByPageNumber>> = jsonMapper.readValue(json)

  final override fun parseContextMapping(json: String): MigrationMessage<*, Any> = jsonMapper.readValue(json)

  abstract override fun parseContextNomisId(json: String): MigrationMessage<*, NOMIS_ID>

  // uses mapping service in super class - will prevent that call
  final override suspend fun getMigrationCount(migrationId: String): Long = 0
}
