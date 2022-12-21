package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.generateBatchId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.MIGRATE_SENTENCE_ADJUSTMENTS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.MIGRATE_SENTENCING_ADJUSTMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.SENTENCING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisSentencingAdjustmentId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.asStringOrBlank
import java.time.Duration
import java.time.LocalDateTime

@Service
class SentencingMigrationService(
  private val queueService: MigrationQueueService,
  private val nomisApiService: NomisApiService,
  private val migrationHistoryService: MigrationHistoryService,
  private val telemetryClient: TelemetryClient,
  private val auditService: AuditService,
  private val sentencingService: SentencingService,
  private val sentencingMappingService: SentencingMappingService,
  @Value("\${sentencing.page.size:1000}") private val pageSize: Long
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun migrateSentenceAdjustments(migrationFilter: SentencingMigrationFilter): MigrationContext<SentencingMigrationFilter> {
    val count = nomisApiService.getSentenceAdjustments(
      fromDate = migrationFilter.fromDate,
      toDate = migrationFilter.toDate,
      pageNumber = 0,
      pageSize = 1,
    ).totalElements

    return MigrationContext(
      type = SENTENCING,
      migrationId = generateBatchId(),
      body = migrationFilter,
      estimatedCount = count
    ).apply {
      queueService.sendMessage(MIGRATE_SENTENCE_ADJUSTMENTS, this)
    }.also {
      telemetryClient.trackEvent(
        "nomis-migration-sentencing-started",
        mapOf<String, String>(
          "migrationId" to it.migrationId,
          "sentencingMigrationType" to "Sentence Adjustments",
          "estimatedCount" to it.estimatedCount.toString(),
          "fromDate" to it.body.fromDate.asStringOrBlank(),
          "toDate" to it.body.toDate.asStringOrBlank(),
        ),
        null
      )
      migrationHistoryService.recordMigrationStarted(
        migrationId = it.migrationId,
        migrationType = SENTENCING,
        estimatedRecordCount = it.estimatedCount,
        filter = it.body
      )
      auditService.sendAuditEvent(
        AuditType.MIGRATION_STARTED.name,
        mapOf("migrationType" to SENTENCING.name, "migrationId" to it.migrationId, "filter" to it.body)
      )
    }
  }

  suspend fun divideSentencingAdjustmentsByPage(context: MigrationContext<SentencingMigrationFilter>) {
    (1..context.estimatedCount step pageSize).asSequence()
      .map {
        MigrationContext(
          context = context,
          body = SentencingPage(filter = context.body, pageNumber = it / pageSize, pageSize = pageSize)
        )
      }
      .forEach {
        queueService.sendMessage(SentencingMessages.MIGRATE_SENTENCING_ADJUSTMENTS_BY_PAGE, it)
      }
    queueService.sendMessage(
      SentencingMessages.MIGRATE_SENTENCING_STATUS_CHECK,
      MigrationContext(
        context = context,
        body = SentencingMigrationStatusCheck()
      )
    )
  }

  suspend fun migrateSentenceAdjustmentsForPage(context: MigrationContext<SentencingPage>) =
    nomisApiService.getSentenceAdjustments(
      fromDate = context.body.filter.fromDate,
      toDate = context.body.filter.toDate,
      pageNumber = context.body.pageNumber,
      pageSize = context.body.pageSize
    ).takeUnless {
      migrationHistoryService.isCancelling(context.migrationId)
    }?.content?.map {
      MigrationContext(
        context = context,
        body = it
      )
    }?.forEach { queueService.sendMessage(MIGRATE_SENTENCING_ADJUSTMENT, it) }

  suspend fun migrateSentencingAdjustment(context: MigrationContext<NomisSentencingAdjustmentId>) {
    val nomisAdjustmentId = context.body.adjustmentId
    val nomisAdjustmentType = context.body.adjustmentType

    sentencingMappingService.findNomisSentencingAdjustmentMapping(nomisAdjustmentId, nomisAdjustmentType)?.run {
      log.info("Will not migrate the sentence adjustment since it is migrated already, NOMIS Sentence Adjustment id is $nomisAdjustmentId, type is $nomisAdjustmentType, sentence adjustment id is ${this.sentenceAdjustmentId} as part migration ${this.label ?: "NONE"} (${this.mappingType})")
    }
      ?: run {
        // TODO: get either sentence adjustment or key date adjustment based on adjustment type
        val nomisSentenceAdjustment = nomisApiService.getSentenceAdjustment(nomisAdjustmentId)
        val migratedSentenceAdjustment =
          sentencingService.migrateSentencingAdjustment(nomisSentenceAdjustment.toSentenceAdjustment())
            .also {
              createSentenceAdjustmentMapping(
                nomisAdjustmentId = nomisAdjustmentId,
                nomisAdjustmentType = nomisAdjustmentType,
                sentenceAdjustmentId = it.id,
                context = context
              )
            }
        telemetryClient.trackEvent(
          "nomis-migration-sentence-adjustment-migrated",
          mapOf(
            "nomisAdjustmentId" to nomisAdjustmentId.toString(),
            "nomisAdjustmentType" to nomisAdjustmentType,
            "sentenceAdjustmentId" to migratedSentenceAdjustment.id.toString(),
            "migrationId" to context.migrationId,
          ),
          null
        )
      }
  }

  suspend fun migrateSentencingStatusCheck(context: MigrationContext<SentencingMigrationStatusCheck>) {
    /*
       when checking if there are messages to process, it is always an estimation due to SQS, therefore once
       we think there are no messages we check several times in row reducing probability of false positives significantly
    */
    if (queueService.isItProbableThatThereAreStillMessagesToBeProcessed(context.type)) {
      queueService.sendMessage(
        SentencingMessages.MIGRATE_SENTENCING_STATUS_CHECK,
        MigrationContext(
          context = context,
          body = SentencingMigrationStatusCheck()
        ),
        delaySeconds = 10
      )
    } else {
      if (context.body.hasCheckedAReasonableNumberOfTimes()) {
        telemetryClient.trackEvent(
          "nomis-migration-sentencing-completed",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            // todo indicate which sentencing migration entity was migrated
            "estimatedCount" to context.estimatedCount.toString(),
            "durationMinutes" to context.durationMinutes().toString()
          ),
          null
        )
        migrationHistoryService.recordMigrationCompleted(
          migrationId = context.migrationId,
          recordsFailed = queueService.countMessagesThatHaveFailed(context.type),
          recordsMigrated = sentencingMappingService.getMigrationCount(context.migrationId),
        )
      } else {
        queueService.sendMessage(
          SentencingMessages.MIGRATE_SENTENCING_STATUS_CHECK,
          MigrationContext(
            context = context,
            body = context.body.increment()
          ),
          delaySeconds = 1
        )
      }
    }
  }

  suspend fun cancelMigrateSentencingStatusCheck(context: MigrationContext<SentencingMigrationStatusCheck>) {
    /*
       when checking if there are messages to process, it is always an estimation due to SQS, therefore once
       we think there are no messages we check several times in row reducing probability of false positives significantly
    */
    if (queueService.isItProbableThatThereAreStillMessagesToBeProcessed(context.type)) {
      queueService.purgeAllMessages(context.type)
      queueService.sendMessage(
        SentencingMessages.CANCEL_MIGRATE_SENTENCING,
        MigrationContext(
          context = context,
          body = SentencingMigrationStatusCheck()
        ),
        delaySeconds = 10
      )
    } else {
      if (context.body.hasCheckedAReasonableNumberOfTimes()) {
        telemetryClient.trackEvent(
          "nomis-migration-sentencing-cancelled",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            // todo identify sentencing entity
            "estimatedCount" to context.estimatedCount.toString(),
            "durationMinutes" to context.durationMinutes().toString()
          ),
          null
        )
        migrationHistoryService.recordMigrationCancelled(
          migrationId = context.migrationId,
          recordsFailed = queueService.countMessagesThatHaveFailed(context.type),
          recordsMigrated = sentencingMappingService.getMigrationCount(context.migrationId),
        )
      } else {
        queueService.purgeAllMessages(context.type)
        queueService.sendMessage(
          SentencingMessages.CANCEL_MIGRATE_SENTENCING,
          MigrationContext(
            context = context,
            body = context.body.increment()
          ),
          delaySeconds = 1
        )
      }
    }
  }

  suspend fun cancel(migrationId: String) {
    val migration = migrationHistoryService.get(migrationId)
    telemetryClient.trackEvent(
      "nomis-migration-sentencing-cancel-requested",
      mapOf<String, String>(
        "migrationId" to migration.migrationId,
      ),
      null
    )
    migrationHistoryService.recordMigrationCancelledRequested(migrationId)
    auditService.sendAuditEvent(
      AuditType.MIGRATION_CANCEL_REQUESTED.name,
      mapOf("migrationType" to SENTENCING.name, "migrationId" to migration.migrationId)
    )
    queueService.purgeAllMessagesNowAndAgainInTheNearFuture(
      MigrationContext(
        context = MigrationContext(type = SENTENCING, migrationId, migration.estimatedRecordCount, Unit),
        body = SentencingMigrationStatusCheck()
      ),
      message = SentencingMessages.CANCEL_MIGRATE_SENTENCING
    )
  }

  private suspend fun createSentenceAdjustmentMapping(
    nomisAdjustmentId: Long,
    nomisAdjustmentType: String,
    sentenceAdjustmentId: Long,
    context: MigrationContext<*>
  ) = try {
    sentencingMappingService.createNomisSentencingAdjustmentMigrationMapping(
      nomisAdjustmentId = nomisAdjustmentId,
      nomisAdjustmentType = nomisAdjustmentType,
      sentenceAdjustmentId = sentenceAdjustmentId,
      migrationId = context.migrationId,
    )
  } catch (e: Exception) {
    log.error(
      "Failed to create mapping for  adjustment nomis id: $nomisAdjustmentId and type $nomisAdjustmentType, sentence adjustment id $sentenceAdjustmentId",
      e
    )
    queueService.sendMessage(
      SentencingMessages.RETRY_SENTENCING_ADJUSTMENT_MAPPING,
      MigrationContext(
        context = context,
        body = SentencingAdjustmentMapping(
          nomisAdjustmentId = nomisAdjustmentId,
          nomisAdjustmentType = nomisAdjustmentType,
          sentenceAdjustmentId = sentenceAdjustmentId
        )
      )
    )
  }

  suspend fun retryCreateSentenceAdjustmentMapping(context: MigrationContext<SentencingAdjustmentMapping>) =
    sentencingMappingService.createNomisSentencingAdjustmentMigrationMapping(
      nomisAdjustmentId = context.body.nomisAdjustmentId,
      nomisAdjustmentType = context.body.nomisAdjustmentType,
      sentenceAdjustmentId = context.body.sentenceAdjustmentId,
      migrationId = context.migrationId,
    )
}

data class SentencingAdjustmentMapping(
  val nomisAdjustmentId: Long,
  val nomisAdjustmentType: String,
  val sentenceAdjustmentId: Long,
)

// TODO move this
private fun <T> MigrationContext<T>.durationMinutes(): Long =
  Duration.between(LocalDateTime.parse(this.migrationId), LocalDateTime.now()).toMinutes()

data class SentencingPage(val filter: SentencingMigrationFilter, val pageNumber: Long, val pageSize: Long)

data class SentencingMigrationStatusCheck(val checkCount: Int = 0) {
  fun hasCheckedAReasonableNumberOfTimes() = checkCount > 9
  fun increment() = this.copy(checkCount = checkCount + 1)
}
