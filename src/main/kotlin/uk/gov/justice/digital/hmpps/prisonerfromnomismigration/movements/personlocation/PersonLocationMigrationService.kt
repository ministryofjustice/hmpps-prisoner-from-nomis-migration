package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonLocationsMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonNumberAndRootOffenderId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByIdRangeMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByLastId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.PERSON_LOCATIONS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class PersonLocationMigrationService(
  mappingApi: PersonLocationMappingApiService,
  private val nomisApi: NomisApiService,
  jsonMapper: JsonMapper,
  @Value($$"${personlocations.page.size:1000}") pageSize: Long,
  @Value($$"${personlocations.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${personlocations.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${personlocations.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : ByIdRangeMigrationService<PersonLocationMigrationFilter, PrisonNumberAndRootOffenderId, PersonLocationsMappingDto>(
  mappingService = mappingApi,
  migrationType = PERSON_LOCATIONS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {

  override suspend fun getTotalNumberOfIds(migrationFilter: PersonLocationMigrationFilter): Long = if (migrationFilter.prisonerNumber.isNullOrBlank()) {
    nomisApi.getPrisonerIds(0, 1).totalElements
  } else {
    1L
  }

  override suspend fun getRangeOfIds(
    body: PersonLocationMigrationFilter,
    pageSize: Long,
  ): List<Pair<PrisonNumberAndRootOffenderId, PrisonNumberAndRootOffenderId>> = if (body.prisonerNumber.isNullOrBlank()) {
    nomisApi.getAllPrisonersIdRanges(pageSize)
      .map { Pair(PrisonNumberAndRootOffenderId(it.fromId, ""), PrisonNumberAndRootOffenderId(it.toId, "")) }
  } else {
    // The prisoner number is supplied to us, so pretend there's a single range to get
    listOf(PrisonNumberAndRootOffenderId(0, "") to PrisonNumberAndRootOffenderId(1, ""))
  }

  override suspend fun getPageOfIdsFromIdRange(
    firstId: PrisonNumberAndRootOffenderId?,
    lastId: PrisonNumberAndRootOffenderId?,
    migrationFilter: PersonLocationMigrationFilter,
  ): List<PrisonNumberAndRootOffenderId> = if (migrationFilter.prisonerNumber.isNullOrBlank()) {
    nomisApi.getAllPrisonersInRange(firstId!!.rootOffenderId, lastId!!.rootOffenderId)
  } else {
    // If a single prisoner migration is requested, then we'll trust the input as we're probably testing. Pretend that we called nomis-prisoner-api which found a single prisoner.
    listOf(PrisonNumberAndRootOffenderId(0, migrationFilter.prisonerNumber))
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonNumberAndRootOffenderId>) {
    // TODO SDIT-4312 - implement the person location migration for a single prisoner
  }

  override suspend fun retryCreateMapping(context: MigrationContext<PersonLocationsMappingDto>) {
    // TODO SDIT-4312 - implement retrying the person location mappings
  }

  override fun parseContextFilter(json: String): MigrationMessage<*, PersonLocationMigrationFilter> = jsonMapper.readValue(json)
  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<PersonLocationMigrationFilter, ByLastId<PrisonNumberAndRootOffenderId>>> = jsonMapper.readValue(json)
  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonNumberAndRootOffenderId> = jsonMapper.readValue(json)
  override fun parseContextMapping(json: String): MigrationMessage<*, PersonLocationsMappingDto> = jsonMapper.readValue(json)
}
