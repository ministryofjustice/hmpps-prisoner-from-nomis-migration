package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonPersonMigrationMappingRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service("prisonPersonMigrationService")
class MigrationService(
  queueService: MigrationQueueService,
  private val nomisService: NomisApiService,
  @Qualifier("prisonPersonMappingApiService") private val mappingApiService: MappingApiService,
  migrationHistoryService: MigrationHistoryService,
  telemetryClient: TelemetryClient,
  auditService: AuditService,
  private val entityMigratorService: EntityMigratorService,
  @Value("\${page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : MigrationService<MigrationFilter, MigrationRequest, PrisonerPhysicalAttributesResponse, PrisonPersonMigrationMappingRequest>(
  queueService = queueService,
  auditService = auditService,
  migrationHistoryService = migrationHistoryService,
  mappingService = mappingApiService,
  telemetryClient = telemetryClient,
  migrationType = MigrationType.PRISONPERSON,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {
  private suspend fun migrateEntity(
    offenderNo: String,
    migrationType: PrisonPersonMigrationMappingRequest.MigrationType,
  ): DpsResponse = entityMigratorService.migrator(migrationType).migrate(offenderNo)

  override suspend fun getIds(
    migrationFilter: MigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<MigrationRequest> =
    if (migrationFilter.prisonerNumber.isNullOrEmpty()) {
      nomisService.getPrisonerIds(
        pageNumber = pageNumber,
        pageSize = pageSize,
      ).let {
        PageImpl<MigrationRequest>(
          it.content.map { MigrationRequest(it.offenderNo, migrationFilter.migrationType) },
          it.pageable,
          it.totalElements,
        )
      }
    } else {
      // If a single prisoner migration is requested then we must be testing. Pretend that we called nomis-prisoner-api which found a single prisoner.
      PageImpl<MigrationRequest>(mutableListOf(MigrationRequest(migrationFilter.prisonerNumber, migrationFilter.migrationType)), Pageable.ofSize(1), 1)
    }

  override suspend fun migrateNomisEntity(context: MigrationContext<MigrationRequest>) {
    log.info("attempting to migrate ${context.body}")
    val offenderNo = context.body.prisonerNumber
    val telemetry = mutableMapOf(
      "offenderNo" to offenderNo,
      "migrationId" to context.migrationId,
      "migrationType" to "PRISON_PERSON",
      "prisonPersonMigrationType" to context.body.migrationType,
    )

    try {
      val dpsIds = migrateEntity(context.body.prisonerNumber, context.body.migrationType).ids
      telemetry["dpsIds"] = dpsIds.toString()

      PrisonPersonMigrationMappingRequest(
        nomisPrisonerNumber = offenderNo,
        migrationType = context.body.migrationType,
        label = context.migrationId,
        dpsIds = dpsIds,
      ).createActivityMapping(context)

      telemetryClient.trackEvent("prisonperson-migration-entity-migrated", telemetry)
    } catch (e: Exception) {
      telemetry["error"] = e.message ?: "unknown error"
      telemetryClient.trackEvent("prisonperson-migration-entity-failed", telemetry)
      throw e
    }
  }

  private suspend fun PrisonPersonMigrationMappingRequest.createActivityMapping(context: MigrationContext<*>) =
    try {
      mappingApiService.createMapping(this, object : ParameterizedTypeReference<DuplicateErrorResponse<PrisonPersonMigrationMappingRequest>>() {})
        .also { it.handleError(context) }
    } catch (e: Exception) {
      log.error(
        "Failed to create activity mapping for nomisPrisonerNumber: $nomisPrisonerNumber, dpsIds $dpsIds for migration ${this.label}",
        e,
      )
      queueService.sendMessage(
        MigrationMessageType.RETRY_MIGRATION_MAPPING,
        MigrationContext(
          context = context,
          body = this,
        ),
      )
    }

  private fun CreateMappingResult<PrisonPersonMigrationMappingRequest>.handleError(context: MigrationContext<*>) =
    takeIf { it.isError }
      ?.let { it.errorResponse?.moreInfo }
      ?.also {
        telemetryClient.trackEvent(
          "prisonperson-nomis-migration-duplicate",
          mapOf(
            "migrationId" to context.migrationId,
            "duplicateNomisPrisonerNumber" to it.duplicate.nomisPrisonerNumber,
            "duplicateDpsIds" to it.duplicate.dpsIds.toString(),
            "existingNomisPrisonerNumber" to it.existing.nomisPrisonerNumber,
            "existingDpsIds" to it.existing.dpsIds.toString(),
          ),
          null,
        )
      }

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}

class MigrationRequest(val prisonerNumber: String, val migrationType: PrisonPersonMigrationMappingRequest.MigrationType)
class DpsResponse(val ids: List<Long>)
