package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SimpleMigrationService

/**
 * Skeleton migration - no NOMIS or DPS calls are made yet.
 */
@Service
class CalculateReleaseDatesMigrationService(
  @Qualifier("mappingApiWebClient") webClient: WebClient,
  jsonMapper: JsonMapper,
  @Value($$"${calculatereleasedates.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${calculatereleasedates.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${calculatereleasedates.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : SimpleMigrationService<PrisonerId>(
  webClient,
  migrationType = MigrationType.CALCULATE_RELEASE_DATES,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {
  private companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonerId>) {
    log.info("Calculate release dates migration of prisoner {} is not yet implemented", context.body.offenderNo)
  }

  override suspend fun getIds(): List<PrisonerId> = emptyList()

  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonerId> = jsonMapper.readValue(json)
}
