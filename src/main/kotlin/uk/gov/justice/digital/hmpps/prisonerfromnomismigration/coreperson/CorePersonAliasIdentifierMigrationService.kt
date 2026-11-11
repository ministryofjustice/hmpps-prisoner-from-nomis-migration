package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonAliasIdentifierMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.CORE_PERSON_ALIAS_IDENTIFIER
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class CorePersonAliasIdentifierMigrationService(
  private val corePersonAliasIdentifierMappingService: CorePersonAliasIdentifierMappingService,
  nomisApiService: NomisApiService,
  jsonMapper: JsonMapper,
  @Value($$"${coreperson.page.size:1000}") pageSize: Long,
  @Value($$"${coreperson.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${coreperson.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${coreperson.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds}") completeCheckScheduledRetrySeconds: Int,
) : CorePersonMigrationService<CorePersonAliasIdentifierMappingsDto>(
  mappingService = corePersonAliasIdentifierMappingService,
  migrationType = CORE_PERSON_ALIAS_IDENTIFIER,
  nomisApiService = nomisApiService,
  jsonMapper = jsonMapper,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
) {

  override suspend fun getExistingMapping(prisonNumber: String): CorePersonMappingDto? = corePersonAliasIdentifierMappingService.getCorePersonByPrisonNumberOrNull(prisonNumber = prisonNumber)

  override suspend fun migratePrisoner(prisonNumber: String, migrationId: String): CorePersonAliasIdentifierMappingsDto {
    TODO("Migrate aliases and identifiers for $prisonNumber")
  }

  override suspend fun createMappingOrOnFailureDo(
    context: MigrationContext<*>,
    mapping: CorePersonAliasIdentifierMappingsDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    TODO("Create alias and identifier mappings")
  }

  override fun parseContextMapping(json: String): MigrationMessage<*, CorePersonAliasIdentifierMappingsDto> = jsonMapper.readValue(json)
}
