package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.generateBatchId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.MIGRATE_SENTENCE_ADJUSTMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.MIGRATE_SENTENCE_ADJUSTMENTS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.SENTENCING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SentenceAdjustmentId
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
        "nomis-migration-sentence-adjustments-started",
        mapOf<String, String>(
          "migrationId" to it.migrationId,
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

  fun divideSentenceAdjustmentsByPage(context: MigrationContext<SentencingMigrationFilter>) {
    (1..context.estimatedCount step pageSize).asSequence()
      .map {
        MigrationContext(
          context = context,
          body = SentencingPage(filter = context.body, pageNumber = it / pageSize, pageSize = pageSize)
        )
      }
      .forEach {
        queueService.sendMessage(SentencingMessages.MIGRATE_SENTENCE_ADJUSTMENTS_BY_PAGE, it)
      }
    queueService.sendMessage(
      SentencingMessages.MIGRATE_SENTENCING_STATUS_CHECK,
      MigrationContext(
        context = context,
        body = SentencingMigrationStatusCheck()
      )
    )
  }

  fun migrateSentenceAdjustmentsForPage(context: MigrationContext<SentencingPage>) =
    nomisApiService.getSentenceAdjustmentsBlocking(
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
    }?.forEach { queueService.sendMessage(MIGRATE_SENTENCE_ADJUSTMENT, it) }

  fun migrateSentenceAdjustment(context: MigrationContext<SentenceAdjustmentId>) {
    val nomisSentenceAdjustmentId = context.body.sentenceAdjustmentId

    sentencingMappingService.findNomisSentenceAdjustmentMapping(nomisSentenceAdjustmentId)?.run {
      log.info("Will not migrate the sentence adjustment since it is migrated already, NOMIS Sentence Adjustment id is $nomisSentenceAdjustmentId, sentence adjustment id is ${this.sentenceAdjustmentId} as part migration ${this.label ?: "NONE"} (${this.mappingType})")
    }
      ?: run {
        val nomisSentenceAdjustment = nomisApiService.getSentenceAdjustmentBlocking(nomisSentenceAdjustmentId)
        val migratedSentenceAdjustment = sentencingService.migrateSentenceAdjustment(nomisSentenceAdjustment.toSentenceAdjustment())
          .also {
            createSentenceAdjustmentMapping(
              nomisSentenceAdjustmentId,
              sentenceAdjustmentId = it.id,
              context = context
            )
          }
        telemetryClient.trackEvent(
          "nomis-migration-sentence-adjustment-migrated",
          mapOf(
            "nomisSentenceAdjustmentId" to nomisSentenceAdjustmentId.toString(),
            "sentenceAdjustmentId" to migratedSentenceAdjustment.id.toString(),
            "migrationId" to context.migrationId,
          ),
          null
        )
      }
  }

  fun migrateSentencingStatusCheck(context: MigrationContext<SentencingMigrationStatusCheck>) {
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

  fun cancelMigrateSentencingStatusCheck(context: MigrationContext<SentencingMigrationStatusCheck>) {
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

  private fun createSentenceAdjustmentMapping(
    nomisSentenceAdjustmentId: Long,
    sentenceAdjustmentId: Long,
    context: MigrationContext<*>
  ) = try {
    sentencingMappingService.createNomisSentenceAdjustmentMigrationMapping(
      nomisSentenceAdjustmentId = nomisSentenceAdjustmentId,
      sentenceAdjustmentId = sentenceAdjustmentId,
      migrationId = context.migrationId,
    )
  } catch (e: Exception) {
    log.error(
      "Failed to create mapping for sentence adjustment nomis id: $nomisSentenceAdjustmentId, sentence adjustment id $sentenceAdjustmentId",
      e
    )
    queueService.sendMessage(
      SentencingMessages.RETRY_SENTENCE_ADJUSTMENT_MAPPING,
      MigrationContext(
        context = context,
        body = SentenceAdjustmentMapping(
          nomisSentenceAdjustmentId = nomisSentenceAdjustmentId,
          sentenceAdjustmentId = sentenceAdjustmentId
        )
      )
    )
  }

  fun retryCreateSentenceAdjustmentMapping(context: MigrationContext<SentenceAdjustmentMapping>) =
    sentencingMappingService.createNomisSentenceAdjustmentMigrationMapping(
      nomisSentenceAdjustmentId = context.body.nomisSentenceAdjustmentId,
      sentenceAdjustmentId = context.body.sentenceAdjustmentId,
      migrationId = context.migrationId,
    )
}

data class SentenceAdjustmentMapping(
  val nomisSentenceAdjustmentId: Long,
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
