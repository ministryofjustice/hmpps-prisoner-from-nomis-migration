package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping

/**
 * A base class for a simple migration with no FILTER and no MAPPING, which divides the NOMIS ids
 * into ranges (see [getRangeOfIds]) rather than paging by page number. Each range is sent as a
 * MIGRATE_BY_PAGE message, the ids within the range are retrieved via [getPageOfIdsFromIdRange]
 * and each id is then migrated directly via [migrateNomisEntity].
 *
 * This is the by id range equivalent of [PaginatedSimpleMigrationService] and is suitable where
 * the ids are not suited to paging by page number, e.g. very large tables where the ids are better
 * split by ranges of the key.
 *
 * Subclasses must implement [parseContextPageFilter] since the type of the id is not known here.
 */
abstract class ByIdRangeSimpleMigrationService<NOMIS_ID : Any>(
  mappingApiWebClient: WebClient,
  migrationType: MigrationType,
  pageSize: Long,
  completeCheckDelaySeconds: Int,
  completeCheckCount: Int,
  completeCheckRetrySeconds: Int = 1,
  completeCheckScheduledRetrySeconds: Int = completeCheckDelaySeconds,
  jsonMapper: JsonMapper,
) : ByIdRangeMigrationService<Any, NOMIS_ID, Any>(
  // no mapping api is used for this type of migration - placeholder implementation
  mappingService = object : MigrationMapping<Any>(domainUrl = "", webClient = mappingApiWebClient) {},
  migrationType = migrationType,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {

  // there is no filter for this type of migration so only the page size is needed
  abstract suspend fun getIdRanges(pageSize: Long): List<Pair<NOMIS_ID, NOMIS_ID>>

  abstract suspend fun getIdsInRange(firstId: NOMIS_ID, lastId: NOMIS_ID): List<NOMIS_ID>

  final override suspend fun getRangeOfIds(body: Any, pageSize: Long): List<Pair<NOMIS_ID, NOMIS_ID>> = getIdRanges(pageSize)

  final override suspend fun getPageOfIdsFromIdRange(
    firstId: NOMIS_ID?,
    lastId: NOMIS_ID?,
    migrationFilter: Any,
  ): List<NOMIS_ID> = getIdsInRange(firstId!!, lastId!!)

  // the total is not known up front when dividing by id range
  override suspend fun getTotalNumberOfIds(migrationFilter: Any): Long = -1

  final override fun parseContextFilter(json: String): MigrationMessage<*, Any> = jsonMapper.readValue(json)

  final override fun parseContextMapping(json: String): MigrationMessage<*, Any> = jsonMapper.readValue(json)

  abstract override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<Any, ByLastId<NOMIS_ID>>>

  abstract override fun parseContextNomisId(json: String): MigrationMessage<*, NOMIS_ID>

  // uses mapping service in super class - will prevent that call
  final override suspend fun getMigrationCount(migrationId: String): Long = 0
}
