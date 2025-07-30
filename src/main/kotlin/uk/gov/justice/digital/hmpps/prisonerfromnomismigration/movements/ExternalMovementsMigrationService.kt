package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class ExternalMovementsMigrationService(
  val migrationMappingService: ExternalMovementsMappingApiService,
  val nomisIdsApiService: NomisApiService,
  val externalMovementsNomisApiService: ExternalMovementsNomisApiService,
  @Value($$"${externalmovements.page.size:1000}") pageSize: Long,
  @Value($$"${externalmovements.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value($$"${externalmovements.complete-check.retry-seconds:1}") completeCheckRetrySeconds: Int,
  @Value($$"${externalmovements.complete-check.count}") completeCheckCount: Int,
  @Value($$"${complete-check.scheduled-retry-seconds:10}") completeCheckScheduledRetrySeconds: Int,
) : MigrationService<ExternalMovementsMigrationFilter, PrisonerId, ExternalMovementsMigrationMappingDto>(
  mappingService = migrationMappingService,
  migrationType = MigrationType.EXTERNAL_MOVEMENTS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  completeCheckRetrySeconds = completeCheckRetrySeconds,
  completeCheckScheduledRetrySeconds = completeCheckScheduledRetrySeconds,
) {
  override suspend fun getIds(
    migrationFilter: ExternalMovementsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<PrisonerId> = if (migrationFilter.prisonerNumber.isNullOrEmpty()) {
    nomisIdsApiService.getPrisonerIds(
      pageNumber = pageNumber,
      pageSize = pageSize,
    )
  } else {
    // If a single prisoner migration is requested, then we'll trust the input as we're probably testing. Pretend that we called nomis-prisoner-api which found a single prisoner.
    PageImpl<PrisonerId>(mutableListOf(PrisonerId(migrationFilter.prisonerNumber)), Pageable.ofSize(1), 1)
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonerId>) {
    val prisonerId = context.body
    externalMovementsNomisApiService.getTemporaryAbsences(prisonerId.offenderNo)

    // TODO SDIT-2874 Migrate to DPS, create mappings, publish telemetry
  }

  override suspend fun retryCreateMapping(context: MigrationContext<ExternalMovementsMigrationMappingDto>) {
    createMappingOrOnFailureDo(context.body) {
      throw it
    }
  }

  private suspend fun createMappingOrOnFailureDo(
    mapping: ExternalMovementsMigrationMappingDto,
    failureHandler: suspend (error: Throwable) -> Unit,
  ) {
    runCatching {
      createMapping(mapping)
    }.onSuccess {
      telemetryClient.trackEvent(
        "external-movements-migration-entity-migrated",
        mapOf(
          "offenderNo" to mapping.prisonerNumber,
          "migrationId" to mapping.migrationId,
        ),
        null,
      )
    }.onFailure {
      failureHandler(it)
    }
  }

  private suspend fun createMapping(mapping: ExternalMovementsMigrationMappingDto) {
    migrationMappingService.createMapping(
      mapping,
      object :
        ParameterizedTypeReference<DuplicateErrorResponse<ExternalMovementsMigrationMappingDto>>() {},
    )
  }

  private suspend fun requeueCreateMapping(
    mapping: ExternalMovementsMigrationMappingDto,
    context: MigrationContext<*>,
  ) {
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = mapping,
      ),
    )
  }
}
