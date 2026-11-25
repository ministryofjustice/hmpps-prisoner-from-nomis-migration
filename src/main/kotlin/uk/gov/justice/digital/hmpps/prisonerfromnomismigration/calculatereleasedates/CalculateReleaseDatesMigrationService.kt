package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonNumberAndRootOffenderId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByIdRangeMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByLastId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class CalculateReleaseDatesMigrationService(
  @Qualifier("mappingApiWebClient") mappingApiWebClient: WebClient,
  private val nomisApi: NomisApiService,
  private val calculateReleaseDatesNomisApi: CalculateReleaseDatesNomisApiService,
  private val dpsApi: CalculateReleaseDatesApiService,
  jsonMapper: JsonMapper,
  @Value($$"${calculatereleasedates.page.size:1000}") pageSize: Long,
  @Value($$"${calculatereleasedates.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${calculatereleasedates.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${calculatereleasedates.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : ByIdRangeMigrationService<CalculateReleaseDatesMigrationFilter, PrisonNumberAndRootOffenderId, Any>(
  // no mapping api is used for this type of migration - placeholder implementation to satisfy the base class
  mappingService = object : MigrationMapping<Any>(domainUrl = "", webClient = mappingApiWebClient) {},
  migrationType = MigrationType.CALCULATE_RELEASE_DATES,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {

  override suspend fun getTotalNumberOfIds(migrationFilter: CalculateReleaseDatesMigrationFilter): Long = if (migrationFilter.prisonerNumber.isNullOrBlank()) {
    nomisApi.getPrisonerIds(0, 1).totalElements
  } else {
    1L
  }

  override suspend fun getRangeOfIds(
    body: CalculateReleaseDatesMigrationFilter,
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
    migrationFilter: CalculateReleaseDatesMigrationFilter,
  ): List<PrisonNumberAndRootOffenderId> = if (migrationFilter.prisonerNumber.isNullOrBlank()) {
    nomisApi.getAllPrisonersInRange(firstId!!.rootOffenderId, lastId!!.rootOffenderId)
  } else {
    // A single prisoner migration trusts the input rather than calling nomis-prisoner-api
    listOf(PrisonNumberAndRootOffenderId(0, migrationFilter.prisonerNumber))
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonNumberAndRootOffenderId>) {
    val offenderNo = context.body.prisonNumber
    val telemetry = mutableMapOf(
      "offenderNo" to offenderNo,
      "migrationId" to context.migrationId,
    )

    runCatching {
      val calculations = calculateReleaseDatesNomisApi.getSentenceCalculationsForPrisoner(offenderNo)
      if (calculations.isEmpty()) {
        publishTelemetry("ignored", telemetry.apply { this["reason"] = "The offender has no sentence calculations" })
        return
      }
      dpsApi.migratePrisoner(offenderNo, calculations.toMigrationRequest())
      publishTelemetry("migrated", telemetry.apply { this["count"] = calculations.size.toString() })
    }.onFailure {
      publishTelemetry("failed", telemetry.apply { this["reason"] = it.message ?: "Unknown error" })
      throw it
    }
  }

  private fun publishTelemetry(type: String, telemetry: Map<String, String>) {
    telemetryClient.trackEvent("calculatereleasedates-migration-entity-$type", telemetry)
  }

  // there is no mapping for this type of migration
  override suspend fun getMigrationCount(migrationId: String): Long = 0

  override fun parseContextFilter(json: String): MigrationMessage<*, CalculateReleaseDatesMigrationFilter> = jsonMapper.readValue(json)
  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<CalculateReleaseDatesMigrationFilter, ByLastId<PrisonNumberAndRootOffenderId>>> = jsonMapper.readValue(json)
  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonNumberAndRootOffenderId> = jsonMapper.readValue(json)
  override fun parseContextMapping(json: String): MigrationMessage<*, Any> = jsonMapper.readValue(json)
}
