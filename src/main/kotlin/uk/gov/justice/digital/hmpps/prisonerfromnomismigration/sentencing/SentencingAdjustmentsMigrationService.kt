package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AdjustmentIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.SENTENCING_ADJUSTMENTS

@Service
class SentencingAdjustmentsMigrationService(
  private val nomisApiService: SentencingAdjustmentsNomisApiService,
  private val sentencingService: SentencingService,
  private val sentencingAdjustmentsMappingService: SentencingAdjustmentsMappingService,
  @Value("\${sentencing.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : MigrationService<SentencingMigrationFilter, AdjustmentIdResponse, SentencingAdjustmentNomisMapping>(
  mappingService = sentencingAdjustmentsMappingService,
  migrationType = SENTENCING_ADJUSTMENTS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(
    migrationFilter: SentencingMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<AdjustmentIdResponse> = nomisApiService.getSentencingAdjustmentIds(
    fromDate = migrationFilter.fromDate,
    toDate = migrationFilter.toDate,
    pageNumber = pageNumber,
    pageSize = pageSize,
  )

  override suspend fun migrateNomisEntity(context: MigrationContext<AdjustmentIdResponse>) {
    log.info("attempting to migrate patch ${context.body}")
    val nomisAdjustmentId = context.body.adjustmentId
    val nomisAdjustmentCategory = context.body.adjustmentCategory

    val mapping = sentencingAdjustmentsMappingService.findNomisSentencingAdjustmentMapping(nomisAdjustmentId, nomisAdjustmentCategory)
    val nomisAdjustment =
      if (nomisAdjustmentCategory == "SENTENCE") {
        nomisApiService.getSentenceAdjustment(nomisAdjustmentId)
      } else {
        nomisApiService.getKeyDateAdjustment(nomisAdjustmentId)
      }

    sentencingService.patchSentencingAdjustmentCurrentTerm(mapping.adjustmentId, nomisAdjustment!!.toSentencingAdjustment())
    telemetryClient.trackEvent(
      "sentencing-adjustments-migration-entity-patched",
      mapOf(
        "nomisAdjustmentId" to nomisAdjustmentId.toString(),
        "nomisAdjustmentCategory" to nomisAdjustmentCategory,
        "adjustmentId" to mapping.adjustmentId,
        "migrationId" to context.migrationId,
      ),
      null,
    )
  }
}
