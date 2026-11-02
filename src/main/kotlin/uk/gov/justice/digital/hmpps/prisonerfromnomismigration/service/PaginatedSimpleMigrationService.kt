package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType

/**
 * A base class for a simple migration with no FILTER and no MAPPING, but which does paginate
 * through NOMIS ids (see [getIds]) rather than retrieving them all in one go. Each page of ids
 * is sent as a MIGRATE_BY_PAGE message and each id within a page is then migrated directly via
 * [migrateNomisEntity].
 *
 * This is suitable for simple migrations where the volume of data is too large to retrieve and
 * queue in one go, but there is no need to record a NOMIS id to DPS id mapping, e.g. where the
 * migration is idempotent or the DPS id is deterministic from the NOMIS id.
 */
abstract class PaginatedSimpleMigrationService<NOMIS_ID : Any>(
  mappingApiWebClient: WebClient,
  migrationType: MigrationType,
  private val pageSize: Long,
  completeCheckDelaySeconds: Int,
  completeCheckCount: Int,
  completeCheckRetrySeconds: Int = 1,
  completeCheckScheduledRetrySeconds: Int = completeCheckDelaySeconds,
  jsonMapper: JsonMapper,
) : MigrationService<Any, NOMIS_ID, Any, ByPageNumber>(
  // no mapping api is used for this type of migration - placeholder implementation
  mappingService = object : MigrationMapping<Any>(domainUrl = "", webClient = mappingApiWebClient) {},
  migrationType = migrationType,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {

  // there is no filter for this type of migration so pageNumber/pageSize are all that is needed
  abstract suspend fun getIds(pageSize: Long, pageNumber: Long): List<NOMIS_ID>

  abstract suspend fun getTotalCount(): Long

  final override suspend fun getPageOfIds(migrationFilter: Any, pageSize: Long, pageNumber: Long): List<NOMIS_ID> = getIds(pageSize, pageNumber)

  final override suspend fun getTotalNumberOfIds(migrationFilter: Any): Long = getTotalCount()

  final override suspend fun divideEntitiesByPage(context: MigrationContext<Any>) {
    (1..context.estimatedCount step pageSize).asSequence()
      .map {
        MigrationContext(
          context = context,
          body = MigrationPage(
            filter = context.body,
            pageKey = ByPageNumber(pageNumber = it / pageSize),
            pageSize = pageSize,
          ),
        )
      }
      .forEach {
        queueService.sendMessage(MigrationMessageType.MIGRATE_BY_PAGE, it)
      }
    startStatusCheck(context)
  }

  final override suspend fun migrateEntitiesForPage(context: MigrationContext<MigrationPage<Any, ByPageNumber>>) {
    getIds(context.body.pageSize, context.body.pageKey.pageNumber).takeUnless {
      migrationHistoryService.isCancelling(context.migrationId)
    }?.forEach { id ->
      queueService.sendMessageNoTracing(
        MigrationMessageType.MIGRATE_ENTITY,
        MigrationContext(context = context, body = id),
      )
    }
  }

  final override fun parseContextFilter(json: String): MigrationMessage<*, Any> = jsonMapper.readValue(json)

  final override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<Any, ByPageNumber>> = jsonMapper.readValue(json)

  final override fun parseContextMapping(json: String): MigrationMessage<*, Any> = jsonMapper.readValue(json)

  abstract override fun parseContextNomisId(json: String): MigrationMessage<*, NOMIS_ID>

  // uses mapping service in super class - will prevent that call
  final override suspend fun getMigrationCount(migrationId: String): Long = 0
}
