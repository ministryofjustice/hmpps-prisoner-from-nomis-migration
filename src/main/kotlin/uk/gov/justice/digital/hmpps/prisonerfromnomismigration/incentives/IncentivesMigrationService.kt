package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.generateBatchId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentiveMessages.CANCEL_MIGRATE_INCENTIVES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentiveMessages.MIGRATE_INCENTIVE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentiveMessages.MIGRATE_INCENTIVES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentiveMessages.MIGRATE_INCENTIVES_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentiveMessages.MIGRATE_INCENTIVES_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.IncentiveId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.INCENTIVES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.asStringOrBlank
import java.time.Duration
import java.time.LocalDateTime

@Service
class IncentivesMigrationService(
  private val queueService: MigrationQueueService,
  private val nomisApiService: NomisApiService,
  private val migrationHistoryService: MigrationHistoryService,
  private val telemetryClient: TelemetryClient,
  private val auditService: AuditService,
  @Value("\${incentives.page.size:1000}") private val pageSize: Long
) {

  suspend fun migrateIncentives(migrationFilter: IncentivesMigrationFilter): MigrationContext<IncentivesMigrationFilter> {
    val incentiveCount = nomisApiService.getIncentives(
      fromDate = migrationFilter.fromDate,
      toDate = migrationFilter.toDate,
      pageNumber = 0,
      pageSize = 1,
    ).totalElements

    return MigrationContext(
      type = INCENTIVES,
      migrationId = generateBatchId(),
      body = migrationFilter,
      estimatedCount = incentiveCount
    ).apply {
      queueService.sendMessage(MIGRATE_INCENTIVES, this)
    }.also {
      telemetryClient.trackEvent(
        "nomis-migration-incentives-started",
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
        migrationType = INCENTIVES,
        estimatedRecordCount = it.estimatedCount,
        filter = it.body
      )
      auditService.sendAuditEvent(
        AuditType.MIGRATION_STARTED.name,
        mapOf("migrationType" to INCENTIVES.name, "migrationId" to it.migrationId, "filter" to it.body)
      )
    }
  }

  fun divideIncentivesByPage(context: MigrationContext<IncentivesMigrationFilter>) {
    (1..context.estimatedCount step pageSize).asSequence()
      .map {
        MigrationContext(
          context = context,
          body = IncentivesPage(filter = context.body, pageNumber = it / pageSize, pageSize = pageSize)
        )
      }
      .forEach {
        queueService.sendMessage(MIGRATE_INCENTIVES_BY_PAGE, it)
      }
    queueService.sendMessage(
      MIGRATE_INCENTIVES_STATUS_CHECK,
      MigrationContext(
        context = context,
        body = IncentiveMigrationStatusCheck()
      )
    )
  }

  fun migrateIncentivesForPage(context: MigrationContext<IncentivesPage>) =
    nomisApiService.getIncentivesBlocking(
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
    }?.forEach { queueService.sendMessage(MIGRATE_INCENTIVE, it) }

  fun migrateIncentive(context: MigrationContext<IncentiveId>) =
    telemetryClient.trackEvent(
      "nomis-migration-incentive-migrated",
      mapOf(
        "migrationId" to context.migrationId,
        "bookingId" to context.body.bookingId.toString(),
        "sequence" to context.body.sequence.toString(),
      ),
      null
    )

  fun migrateIncentivesStatusCheck(context: MigrationContext<IncentiveMigrationStatusCheck>) {
    /*
       when checking if there are messages to process, it is always an estimation due to SQS, therefore once
       we think there are no messages we check several times in row reducing probability of false positives significantly
    */
    if (queueService.isItProbableThatThereAreStillMessagesToBeProcessed(context.type)) {
      queueService.sendMessage(
        MIGRATE_INCENTIVES_STATUS_CHECK,
        MigrationContext(
          context = context,
          body = IncentiveMigrationStatusCheck()
        ),
        delaySeconds = 10
      )
    } else {
      if (context.body.hasCheckedAReasonableNumberOfTimes()) {
        telemetryClient.trackEvent(
          "nomis-migration-incentives-completed",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "estimatedCount" to context.estimatedCount.toString(),
            "durationMinutes" to context.durationMinutes().toString()
          ),
          null
        )
        migrationHistoryService.recordMigrationCompleted(
          migrationId = context.migrationId,
          recordsFailed = queueService.countMessagesThatHaveFailed(context.type),
          recordsMigrated = 0 // TODO calculated migrated
        )
      } else {
        queueService.sendMessage(
          MIGRATE_INCENTIVES_STATUS_CHECK,
          MigrationContext(
            context = context,
            body = context.body.increment()
          ),
          delaySeconds = 1
        )
      }
    }
  }

  fun cancelMigrateIncentivesStatusCheck(context: MigrationContext<IncentiveMigrationStatusCheck>) {
    /*
       when checking if there are messages to process, it is always an estimation due to SQS, therefore once
       we think there are no messages we check several times in row reducing probability of false positives significantly
    */
    if (queueService.isItProbableThatThereAreStillMessagesToBeProcessed(context.type)) {
      queueService.purgeAllMessages(context.type)
      queueService.sendMessage(
        CANCEL_MIGRATE_INCENTIVES,
        MigrationContext(
          context = context,
          body = IncentiveMigrationStatusCheck()
        ),
        delaySeconds = 10
      )
    } else {
      if (context.body.hasCheckedAReasonableNumberOfTimes()) {
        telemetryClient.trackEvent(
          "nomis-migration-incentives-cancelled",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "estimatedCount" to context.estimatedCount.toString(),
            "durationMinutes" to context.durationMinutes().toString()
          ),
          null
        )
        migrationHistoryService.recordMigrationCancelled(
          migrationId = context.migrationId,
          recordsFailed = queueService.countMessagesThatHaveFailed(context.type),
          recordsMigrated = 0 // TODO calculated migrated
        )
      } else {
        queueService.purgeAllMessages(context.type)
        queueService.sendMessage(
          CANCEL_MIGRATE_INCENTIVES,
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
      "nomis-migration-incentive-cancel-requested",
      mapOf<String, String>(
        "migrationId" to migration.migrationId,
      ),
      null
    )
    migrationHistoryService.recordMigrationCancelledRequested(migrationId)
    auditService.sendAuditEvent(
      AuditType.MIGRATION_CANCEL_REQUESTED.name,
      mapOf("migrationType" to INCENTIVES.name, "migrationId" to migration.migrationId)
    )
    queueService.purgeAllMessagesNowAndAgainInTheNearFuture(
      MigrationContext(
        context = MigrationContext(type = INCENTIVES, migrationId, migration.estimatedRecordCount, Unit),
        body = IncentiveMigrationStatusCheck()
      ),
      message = CANCEL_MIGRATE_INCENTIVES
    )
  }
}

// TODO move this
private fun <T> MigrationContext<T>.durationMinutes(): Long =
  Duration.between(LocalDateTime.parse(this.migrationId), LocalDateTime.now()).toMinutes()

data class IncentivesPage(val filter: IncentivesMigrationFilter, val pageNumber: Long, val pageSize: Long)

data class IncentiveMigrationStatusCheck(val checkCount: Int = 0) {
  fun hasCheckedAReasonableNumberOfTimes() = checkCount > 9
  fun increment() = this.copy(checkCount = checkCount + 1)
}
