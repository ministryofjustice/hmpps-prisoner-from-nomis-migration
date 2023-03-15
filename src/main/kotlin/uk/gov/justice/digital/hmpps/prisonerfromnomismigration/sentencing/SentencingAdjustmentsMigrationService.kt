package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.SENTENCING_ADJUSTMENTS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisAdjustment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisAdjustmentId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes

@Service
class SentencingAdjustmentsMigrationService(
  queueService: MigrationQueueService,
  private val nomisApiService: NomisApiService,
  migrationHistoryService: MigrationHistoryService,
  telemetryClient: TelemetryClient,
  auditService: AuditService,
  private val sentencingService: SentencingService,
  private val sentencingAdjustmentsMappingService: SentencingAdjustmentsMappingService,
  @Value("\${sentencing.page.size:1000}") pageSize: Long,
) : MigrationService<SentencingMigrationFilter, NomisAdjustmentId, NomisAdjustment, SentencingAdjustmentNomisMapping>(
  queueService = queueService,
  auditService = auditService,
  migrationHistoryService = migrationHistoryService,
  mappingService = sentencingAdjustmentsMappingService,
  telemetryClient = telemetryClient,
  migrationType = SENTENCING_ADJUSTMENTS,
  pageSize = pageSize,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(
    migrationFilter: SentencingMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<NomisAdjustmentId> {
    return nomisApiService.getSentencingAdjustmentIds(
      fromDate = migrationFilter.fromDate,
      toDate = migrationFilter.toDate,
      pageNumber = pageNumber,
      pageSize = pageSize,
    )
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<NomisAdjustmentId>) {
    log.info("attempting to migrate ${context.body}")
    val nomisAdjustmentId = context.body.adjustmentId
    val nomisAdjustmentCategory = context.body.adjustmentCategory

    sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMapping(nomisAdjustmentId, nomisAdjustmentCategory)
      ?.run {
        log.info("Will not migrate the adjustment since it is migrated already, NOMIS Adjustment id is $nomisAdjustmentId, type is $nomisAdjustmentCategory, sentencing adjustment id is ${this.adjustmentId} as part migration ${this.label ?: "NONE"} (${this.mappingType})")
      }
      ?: run {
        val nomisAdjustment =
          if (nomisAdjustmentCategory == "SENTENCE") {
            nomisApiService.getSentenceAdjustment(nomisAdjustmentId)
          } else {
            nomisApiService.getKeyDateAdjustment(nomisAdjustmentId)
          }

        val migratedSentenceAdjustment =
          sentencingService.migrateSentencingAdjustment(nomisAdjustment.toSentencingAdjustment())
            .also {
              createAdjustmentMapping(
                nomisAdjustmentId = nomisAdjustmentId,
                nomisAdjustmentCategory = nomisAdjustmentCategory,
                adjustmentId = it.id,
                context = context,
              )
            }
        telemetryClient.trackEvent(
          "sentencing-adjustments-migration-entity-migrated",
          mapOf(
            "nomisAdjustmentId" to nomisAdjustmentId.toString(),
            "nomisAdjustmentCategory" to nomisAdjustmentCategory,
            "adjustmentId" to migratedSentenceAdjustment.id,
            "migrationId" to context.migrationId,
          ),
          null,
        )
      }
  }

  private suspend fun createAdjustmentMapping(
    nomisAdjustmentId: Long,
    nomisAdjustmentCategory: String,
    adjustmentId: String,
    context: MigrationContext<*>,
  ) = try {
    sentencingAdjustmentsMappingService.createMapping(
      SentencingAdjustmentNomisMapping(
        nomisAdjustmentId = nomisAdjustmentId,
        nomisAdjustmentCategory = nomisAdjustmentCategory,
        adjustmentId = adjustmentId,
        label = context.migrationId,
        mappingType = "MIGRATED",
      ),
      object : ParameterizedTypeReference<DuplicateErrorResponse<SentencingAdjustmentNomisMapping>>() {},
    ).also {
      if (it.isError) {
        val duplicateErrorDetails = (it.errorResponse!!).moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-adjustment-duplicate",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "duplicateAdjustmentId" to duplicateErrorDetails.duplicate.adjustmentId,
            "duplicateNomisAdjustmentId" to duplicateErrorDetails.duplicate.nomisAdjustmentId.toString(),
            "duplicateNomisAdjustmentCategory" to duplicateErrorDetails.duplicate.nomisAdjustmentCategory,
            "existingAdjustmentId" to duplicateErrorDetails.existing.adjustmentId,
            "existingNomisAdjustmentId" to duplicateErrorDetails.existing.nomisAdjustmentId.toString(),
            "existingNomisAdjustmentCategory" to duplicateErrorDetails.existing.nomisAdjustmentCategory,
            "durationMinutes" to context.durationMinutes().toString(),
          ),
          null,
        )
      }
    }
  } catch (e: Exception) {
    log.error(
      "Failed to create mapping for  adjustment nomis id: $nomisAdjustmentId and type $nomisAdjustmentCategory, sentence adjustment id $adjustmentId",
      e,
    )
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = SentencingAdjustmentNomisMapping(
          nomisAdjustmentId = nomisAdjustmentId,
          nomisAdjustmentCategory = nomisAdjustmentCategory,
          adjustmentId = adjustmentId,
          mappingType = "MIGRATED",
        ),
      ),
    )
  }
}
