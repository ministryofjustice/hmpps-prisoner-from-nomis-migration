package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.data.domain.PageImpl
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.generateBatchId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MessageType
import java.time.Duration
import java.time.LocalDateTime

abstract class MigrationService<FILTER, NOMIS_ID, NOMIS_ENTITY, MAPPING>(
  private val queueService: MigrationQueueService,
  private val migrationHistoryService: MigrationHistoryService,
  private val telemetryClient: TelemetryClient,
  private val auditService: AuditService,
  private val synchronisationType: SynchronisationType,
  private val pageSize: Long
) {

  abstract suspend fun getIds(migrationFilter: FILTER, pageSize: Long, pageNumber: Long): PageImpl<NOMIS_ID>

  abstract fun getTelemetryFromFilter(migrationFilter: FILTER): Map<String, String>

  abstract fun getTelemetryFromNomisEntity(nomisEntity: NOMIS_ENTITY): Map<String, String>

  abstract fun getMigrationType(): SynchronisationType

  abstract suspend fun getMigrationCount(migrationId: String): Long

  abstract suspend fun migrateNomisEntity(context: MigrationContext<NOMIS_ID>)

  abstract suspend fun retryCreateMapping(context: MigrationContext<MAPPING>)

  suspend fun migrateAdjustments(migrationFilter: FILTER): MigrationContext<FILTER> {
    val count = getIds(
      migrationFilter,
      pageSize = 1,
      pageNumber = 0
    ).totalElements

    return MigrationContext(
      type = synchronisationType,
      migrationId = generateBatchId(),
      body = migrationFilter,
      estimatedCount = count
    ).apply {
      queueService.sendMessage(MessageType.MIGRATE_ENTITIES, this)
    }.also {
      telemetryClient.trackEvent(
        "nomis-migration-started",
        mapOf<String, String>(
          "migrationId" to it.migrationId,
          "estimatedCount" to it.estimatedCount.toString()
        ) + getTelemetryFromFilter(migrationFilter),
        null
      )
      migrationHistoryService.recordMigrationStarted(
        migrationId = it.migrationId,
        migrationType = synchronisationType,
        estimatedRecordCount = it.estimatedCount,
        filter = it.body
      )
      auditService.sendAuditEvent(
        AuditType.MIGRATION_STARTED.name,
        mapOf("migrationType" to synchronisationType.name, "migrationId" to it.migrationId, "filter" to it.body)
      )
    }
  }

  suspend fun divideEntitiesByPage(context: MigrationContext<FILTER>) {
    (1..context.estimatedCount step pageSize).asSequence()
      .map {
        MigrationContext(
          context = context,
          body = MigrationPage(filter = context.body, pageNumber = it / pageSize, pageSize = pageSize)
        )
      }
      .forEach {
        queueService.sendMessage(MessageType.MIGRATE_BY_PAGE, it)
      }
    queueService.sendMessage(
      MessageType.MIGRATE_STATUS_CHECK,
      MigrationContext(
        context = context,
        body = MigrationStatusCheck()
      )
    )
  }

  suspend fun migrateEntitiesForPage(context: MigrationContext<MigrationPage<FILTER>>) =
    getIds(context.body.filter, context.body.pageSize, context.body.pageNumber).takeUnless {
      migrationHistoryService.isCancelling(context.migrationId)
    }?.content?.map {
      MigrationContext(
        context = context,
        body = it
      )
    }?.forEach { queueService.sendMessage(MessageType.MIGRATE_ENTITY, it) }

  suspend fun migrateStatusCheck(context: MigrationContext<MigrationStatusCheck>) {
    /*
       when checking if there are messages to process, it is always an estimation due to SQS, therefore once
       we think there are no messages we check several times in row reducing probability of false positives significantly
    */
    if (queueService.isItProbableThatThereAreStillMessagesToBeProcessed(context.type)) {
      queueService.sendMessage(
        MessageType.MIGRATE_STATUS_CHECK,
        MigrationContext(
          context = context,
          body = MigrationStatusCheck()
        ),
        delaySeconds = 10
      )
    } else {
      if (context.body.hasCheckedAReasonableNumberOfTimes()) {
        telemetryClient.trackEvent(
          "nomis-migration-completed",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "migrationType" to context.type.name,
            "estimatedCount" to context.estimatedCount.toString(),
            "durationMinutes" to context.durationMinutes().toString()
          ),
          null
        )
        migrationHistoryService.recordMigrationCompleted(
          migrationId = context.migrationId,
          recordsFailed = queueService.countMessagesThatHaveFailed(context.type),
          recordsMigrated = getMigrationCount(context.migrationId),
        )
      } else {
        queueService.sendMessage(
          MessageType.MIGRATE_STATUS_CHECK,
          MigrationContext(
            context = context,
            body = context.body.increment()
          ),
          delaySeconds = 1
        )
      }
    }
  }

  suspend fun cancelMigrateStatusCheck(context: MigrationContext<MigrationStatusCheck>) {
    /*
       when checking if there are messages to process, it is always an estimation due to SQS, therefore once
       we think there are no messages we check several times in row reducing probability of false positives significantly
    */
    if (queueService.isItProbableThatThereAreStillMessagesToBeProcessed(context.type)) {
      queueService.purgeAllMessages(context.type)
      queueService.sendMessage(
        MessageType.CANCEL_MIGRATION,
        MigrationContext(
          context = context,
          body = MigrationStatusCheck()
        ),
        delaySeconds = 10
      )
    } else {
      if (context.body.hasCheckedAReasonableNumberOfTimes()) {
        telemetryClient.trackEvent(
          "nomis-migration-cancelled",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "migrationType" to context.type.name,
            "estimatedCount" to context.estimatedCount.toString(),
            "durationMinutes" to context.durationMinutes().toString()
          ),
          null
        )
        migrationHistoryService.recordMigrationCancelled(
          migrationId = context.migrationId,
          recordsFailed = queueService.countMessagesThatHaveFailed(context.type),
          recordsMigrated = getMigrationCount(context.migrationId),
        )
      } else {
        queueService.purgeAllMessages(context.type)
        queueService.sendMessage(
          MessageType.CANCEL_MIGRATION,
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
      "nomis-migration-cancel-requested",
      mapOf<String, String>(
        "migrationId" to migration.migrationId,
      ),
      null
    )
    migrationHistoryService.recordMigrationCancelledRequested(migrationId)
    auditService.sendAuditEvent(
      AuditType.MIGRATION_CANCEL_REQUESTED.name,
      mapOf("migrationType" to synchronisationType.name, "migrationId" to migration.migrationId)
    )
    queueService.purgeAllMessagesNowAndAgainInTheNearFuture(
      MigrationContext(
        context = MigrationContext(type = synchronisationType, migrationId, migration.estimatedRecordCount, Unit),
        body = MigrationStatusCheck()
      ),
      message = MessageType.CANCEL_MIGRATION
    )
  }
}

fun <T> MigrationContext<T>.durationMinutes(): Long =
  Duration.between(LocalDateTime.parse(this.migrationId), LocalDateTime.now()).toMinutes()

class MigrationPage<FILTER>(val filter: FILTER, val pageNumber: Long, val pageSize: Long)

data class MigrationStatusCheck(val checkCount: Int = 0) {
  fun hasCheckedAReasonableNumberOfTimes() = checkCount > 9
  fun increment() = this.copy(checkCount = checkCount + 1)
}
