package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.SENTENCING_ADJUSTMENTS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisAdjustment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisAdjustmentId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.asStringOrBlank
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes

@Service
class SentencingAdjustmentsMigrationService(
  private val queueService: MigrationQueueService,
  private val nomisApiService: NomisApiService,
  migrationHistoryService: MigrationHistoryService,
  private val telemetryClient: TelemetryClient,
  auditService: AuditService,
  private val sentencingService: SentencingService,
  private val sentencingAdjustmentsMappingService: SentencingAdjustmentsMappingService,
  @Value("\${sentencing.page.size:1000}") private val pageSize: Long,
) : MigrationService<SentencingMigrationFilter, NomisAdjustmentId, NomisAdjustment, SentencingAdjustmentNomisMapping>(
  queueService = queueService,
  auditService = auditService,
  migrationHistoryService = migrationHistoryService,
  telemetryClient = telemetryClient,
  migrationType = SENTENCING_ADJUSTMENTS,
  pageSize = pageSize,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(migrationFilter: SentencingMigrationFilter, pageSize: Long, pageNumber: Long): PageImpl<NomisAdjustmentId> {
    return nomisApiService.getSentencingAdjustmentIds(
      fromDate = migrationFilter.fromDate,
      toDate = migrationFilter.toDate,
      pageNumber = pageNumber,
      pageSize = pageSize,
    )
  }

  override fun getTelemetryFromFilter(migrationFilter: SentencingMigrationFilter): Map<String, String> {
    return mapOf(
      "migrationType" to "Sentencing Adjustments",
      "fromDate" to migrationFilter.fromDate.asStringOrBlank(),
      "toDate" to migrationFilter.toDate.asStringOrBlank(),
    )
  }

  override fun getTelemetryFromNomisEntity(nomisEntity: NomisAdjustment): Map<String, String> {
    return mapOf(
      "migrationType" to "Sentencing Adjustments",
      "nomisAdjustmentId" to nomisEntity.id.toString(),
      "nomisAdjustmentCategory" to nomisEntity.getAdjustmentCategory(),
    )
  }

  override fun getMigrationType(): MigrationType {
    return SENTENCING_ADJUSTMENTS
  }

  override suspend fun retryCreateMapping(context: MigrationContext<SentencingAdjustmentNomisMapping>) {
    sentencingAdjustmentsMappingService.createNomisSentencingAdjustmentMigrationMapping(
      nomisAdjustmentId = context.body.nomisAdjustmentId,
      nomisAdjustmentCategory = context.body.nomisAdjustmentCategory,
      adjustmentId = context.body.adjustmentId,
      migrationId = context.migrationId,
    )
  }

  override suspend fun getMigrationCount(migrationId: String): Long {
    return sentencingAdjustmentsMappingService.getMigrationCount(migrationId)
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<NomisAdjustmentId>) {
    log.info("attempting to migrate ${context.body}")
    val nomisAdjustmentId = context.body.adjustmentId
    val nomisAdjustmentCategory = context.body.adjustmentCategory

    sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMapping(nomisAdjustmentId, nomisAdjustmentCategory)?.run {
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
          "nomis-migration-sentencing-adjustment-migrated",
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
    sentencingAdjustmentsMappingService.createNomisSentencingAdjustmentMigrationMapping(
      nomisAdjustmentId = nomisAdjustmentId,
      nomisAdjustmentCategory = nomisAdjustmentCategory,
      adjustmentId = adjustmentId,
      migrationId = context.migrationId,
    ).also {
      if (it.isError) {
        val duplicateErrorDetails = it.errorResponse!!.moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-adjustment-duplicate",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "duplicateAdjustmentId" to duplicateErrorDetails.duplicateAdjustment.adjustmentId,
            "duplicateNomisAdjustmentId" to duplicateErrorDetails.duplicateAdjustment.nomisAdjustmentId.toString(),
            "duplicateNomisAdjustmentCategory" to duplicateErrorDetails.duplicateAdjustment.nomisAdjustmentCategory,
            "existingAdjustmentId" to duplicateErrorDetails.existingAdjustment.adjustmentId,
            "existingNomisAdjustmentId" to duplicateErrorDetails.existingAdjustment.nomisAdjustmentId.toString(),
            "existingNomisAdjustmentCategory" to duplicateErrorDetails.existingAdjustment.nomisAdjustmentCategory,
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
