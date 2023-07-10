package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

private fun AdjudicationResponse.toAdjudication(): AdjudicationMigrateRequest =
  AdjudicationMigrateRequest(this.offenderNo, this.adjudicationNumber!!)

@Service
class AdjudicationsMigrationService(
  queueService: MigrationQueueService,
  private val nomisApiService: NomisApiService,
  migrationHistoryService: MigrationHistoryService,
  telemetryClient: TelemetryClient,
  auditService: AuditService,
  private val adjudicationsMappingService: AdjudicationsMappingService,
  private val adjudicationsService: AdjudicationsService,
  @Value("\${appointments.page.size:1000}") pageSize: Long,
) : MigrationService<AdjudicationsMigrationFilter, AdjudicationIdResponse, AdjudicationResponse, AdjudicationMapping>(
  queueService = queueService,
  auditService = auditService,
  migrationHistoryService = migrationHistoryService,
  mappingService = adjudicationsMappingService,
  telemetryClient = telemetryClient,
  migrationType = MigrationType.ADJUDICATIONS,
  pageSize = pageSize,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(
    migrationFilter: AdjudicationsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<AdjudicationIdResponse> {
    return nomisApiService.getAdjudicationIds(
      fromDate = migrationFilter.fromDate,
      toDate = migrationFilter.toDate,
      pageNumber = pageNumber,
      pageSize = pageSize,
      prisonIds = migrationFilter.prisonIds,
    )
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<AdjudicationIdResponse>) {
    log.info("attempting to migrate ${context.body}")
    val adjudicationNumber = context.body.adjudicationNumber

    adjudicationsMappingService.findNomisMapping(adjudicationNumber = adjudicationNumber)
      ?.run {
        log.info("Will not migrate the adjudication since it is migrated already, NOMIS adjudicationNumber is $adjudicationNumber as part of migration ${this.label ?: "NONE"} (${this.mappingType})")
      }
      ?: run {
        val nomisAdjudication = nomisApiService.getAdjudication(adjudicationNumber)

        adjudicationsService.createAdjudication(nomisAdjudication.toAdjudication())
        createAdjudicationMapping(
          adjudicationNumber,
          context = context,
        )

        telemetryClient.trackEvent(
          "adjudications-migration-entity-migrated",
          mapOf(
            "adjudicationNumber" to adjudicationNumber.toString(),
            "migrationId" to context.migrationId,
          ),
          null,
        )
      }
  }

  private suspend fun createAdjudicationMapping(
    adjudicationNumber: Long,
    context: MigrationContext<*>,
  ) = try {
    adjudicationsMappingService.createMapping(
      AdjudicationMapping(
        adjudicationNumber = adjudicationNumber,
        label = context.migrationId,
        mappingType = "MIGRATED",
      ),
    )
  } catch (e: Exception) {
    log.error(
      "Failed to create mapping for adjudicationNumber: $adjudicationNumber",
      e,
    )
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = AdjudicationMapping(
          adjudicationNumber = adjudicationNumber,
          mappingType = "MIGRATED",
        ),
      ),
    )
  }
}
