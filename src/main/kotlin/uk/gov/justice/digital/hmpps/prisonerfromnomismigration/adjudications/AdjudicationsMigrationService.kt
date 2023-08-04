package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationChargeIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AdjudicationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

private fun AdjudicationChargeResponse.toAdjudication(): AdjudicationMigrateRequest =
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
  @Value("\${adjudications.page.size:1000}") pageSize: Long,
) :
  MigrationService<AdjudicationsMigrationFilter, AdjudicationChargeIdResponse, AdjudicationResponse, AdjudicationMapping>(
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
  ): PageImpl<AdjudicationChargeIdResponse> {
    return nomisApiService.getAdjudicationIds(
      fromDate = migrationFilter.fromDate,
      toDate = migrationFilter.toDate,
      pageNumber = pageNumber,
      pageSize = pageSize,
      prisonIds = migrationFilter.prisonIds,
    )
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<AdjudicationChargeIdResponse>) {
    log.info("attempting to migrate ${context.body}")
    val adjudicationNumber = context.body.adjudicationNumber
    val chargeSequence = context.body.chargeSequence

    adjudicationsMappingService.findNomisMapping(
      adjudicationNumber = adjudicationNumber,
      chargeSequence = chargeSequence,
    )
      ?.run {
        log.info("Will not migrate the adjudication since it is migrated already, NOMIS adjudicationNumber is $adjudicationNumber/$chargeSequence as part of migration ${this.label ?: "NONE"} (${this.mappingType})")
      }
      ?: run {
        val nomisAdjudication =
          nomisApiService.getAdjudicationCharge(
            adjudicationNumber = adjudicationNumber,
            chargeSequence = chargeSequence,
          )

        adjudicationsService.createAdjudication(nomisAdjudication.toAdjudication())
        val chargeNumber =
          "${nomisAdjudication.adjudicationNumber}/$chargeSequence" // TODO: returned by DPS but assume it made from these fields
        createAdjudicationMapping(
          adjudicationNumber = adjudicationNumber,
          chargeSequence = chargeSequence,
          chargeNumber = chargeNumber,
          context = context,
        )

        telemetryClient.trackEvent(
          "adjudications-migration-entity-migrated",
          mapOf(
            "adjudicationNumber" to adjudicationNumber.toString(),
            "chargeSequence" to chargeSequence.toString(),
            "chargeNumber" to chargeNumber,
            "migrationId" to context.migrationId,
          ),
          null,
        )
      }
  }

  private suspend fun createAdjudicationMapping(
    adjudicationNumber: Long,
    chargeSequence: Int,
    chargeNumber: String,
    context: MigrationContext<*>,
  ) = try {
    adjudicationsMappingService.createMapping(
      AdjudicationMapping(
        adjudicationNumber = adjudicationNumber,
        chargeSequence = chargeSequence,
        chargeNumber = chargeNumber,
        label = context.migrationId,
        mappingType = "MIGRATED",
      ),
    )
  } catch (e: Exception) {
    log.error(
      "Failed to create mapping for adjudicationNumber: $adjudicationNumber/$chargeSequence",
      e,
    )
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = AdjudicationMapping(
          adjudicationNumber = adjudicationNumber,
          chargeSequence = chargeSequence,
          chargeNumber = chargeNumber,
          mappingType = "MIGRATED",
        ),
      ),
    )
  }
}
