package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AgencyId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AgencyIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SimpleMigrationService

@Service
class AgencyRegistersMigrationService(
  @Qualifier("mappingApiWebClient") webClient: WebClient,
  private val agencyNomisApiService: AgencyNomisApiService,
  private val agencyRegistersDpsApiService: AgencyRegistersDpsApiService,
  jsonMapper: JsonMapper,
  @Value($$"${agencyregisters.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${agencyregisters.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${agencyregisters.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : SimpleMigrationService<AgencyId>(
  // no mapping api is used for this migration - placeholder implementation to satisfy the base class
  webClient,
  migrationType = MigrationType.AGENCY_REGISTERS,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
  jsonMapper = jsonMapper,
) {

  override suspend fun migrateNomisEntity(context: MigrationContext<AgencyId>) {
    val agencyId = context.body.agencyId
    val agency = agencyNomisApiService.getAgency(agencyId)
    agencyRegistersDpsApiService.migrateAgency(agencyId, agency.toLegacyAgencyDto())
    telemetryClient.trackEvent(
      "agency-migration-entity-migrated",
      mapOf(
        "agencyId" to agency.agencyId,
        "type" to agency.type.code,
        "migrationId" to context.migrationId,
      ),
    )
  }

  override suspend fun getIds(): List<AgencyId> {
    val agencyIdsResponse: AgencyIdsResponse = agencyNomisApiService.getAgencyIds()
    return agencyIdsResponse.agencyIds
  }

  override fun parseContextNomisId(json: String): MigrationMessage<*, AgencyId> = jsonMapper.readValue(json)
}
