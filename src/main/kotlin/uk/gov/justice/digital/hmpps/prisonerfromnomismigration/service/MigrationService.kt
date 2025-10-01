package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.BadRequestException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.generateBatchId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import java.time.Duration
import java.time.LocalDateTime

abstract class MigrationService<FILTER : Any, NOMIS_ID : Any, MAPPING : Any>(
  internal val mappingService: MigrationMapping<MAPPING>,
  internal val migrationType: MigrationType,
  private val pageSize: Long,
  private val completeCheckDelaySeconds: Int,
  private val completeCheckCount: Int,
  private val completeCheckRetrySeconds: Int = 1,
  private val completeCheckScheduledRetrySeconds: Int = completeCheckDelaySeconds,
) {

  @Autowired
  protected lateinit var queueService: MigrationQueueService

  @Autowired
  protected lateinit var migrationHistoryService: MigrationHistoryService

  @Autowired
  protected lateinit var telemetryClient: TelemetryClient

  @Autowired
  protected lateinit var auditService: AuditService

  abstract suspend fun getIds(migrationFilter: FILTER, pageSize: Long, pageNumber: Long): PageImpl<NOMIS_ID>

  abstract suspend fun migrateNomisEntity(context: MigrationContext<NOMIS_ID>)

  open suspend fun getContextProperties(migrationFilter: FILTER): MutableMap<String, Any> = mutableMapOf()

  suspend fun getMigrationCount(migrationId: String): Long = mappingService.getMigrationCount(migrationId)

  suspend fun startMigration(migrationFilter: FILTER): MigrationContext<FILTER> {
    // ensure that we aren't in the middle of a migration already
    if (migrationHistoryService.isMigrationInProgress(migrationType)) {
      throw MigrationAlreadyInProgressException("Migration already in progress for $migrationFilter")
    }

    val count = getIds(
      migrationFilter,
      pageSize = 1,
      pageNumber = 0,
    ).totalElements

    return MigrationContext(
      type = migrationType,
      migrationId = generateBatchId(),
      body = migrationFilter,
      estimatedCount = count,
      properties = getContextProperties(migrationFilter),
    ).apply {
      queueService.sendMessage(MigrationMessageType.MIGRATE_ENTITIES, this)
    }.also {
      telemetryClient.trackEvent(
        "${migrationType.telemetryName}-migration-started",
        mapOf(
          "migrationId" to it.migrationId,
          "estimatedCount" to it.estimatedCount.toString(),
        ) + migrationFilter.asMap(),
      )
      migrationHistoryService.recordMigrationStarted(
        migrationId = it.migrationId,
        migrationType = migrationType,
        estimatedRecordCount = it.estimatedCount,
        filter = it.body,
      )
      auditService.sendAuditEvent(
        AuditType.MIGRATION_STARTED.name,
        mapOf("migrationType" to migrationType.name, "migrationId" to it.migrationId, "filter" to it.body),
      )
    }
  }

  suspend fun divideEntitiesByPage(context: MigrationContext<FILTER>) {
    (1..context.estimatedCount step pageSize).asSequence()
      .map {
        MigrationContext(
          context = context,
          body = MigrationPage(filter = context.body, pageNumber = it / pageSize, pageSize = pageSize),
        )
      }
      .forEach {
        queueService.sendMessage(MigrationMessageType.MIGRATE_BY_PAGE, it)
      }
    queueService.sendMessage(
      MigrationMessageType.MIGRATE_STATUS_CHECK,
      MigrationContext(
        context = context,
        body = MigrationStatusCheck(),
      ),
      delaySeconds = completeCheckDelaySeconds,
      // hold back in case none of the MIGRATE_BY_PAGE messages have actually produced any MIGRATE_ENTITY messages yet
      // e.g. the NOMIS API call is so slow it hasn't retried any IDS yet (this is unlikely since by now Oracle should
      // have cached some of the data making subsequent calls quicker.
      // Note, if it takes longer than 10s + (10 * 1s) to produce the first MIGRATE_ENTITY message the migration could
      // complete prematurely - if that happens the delay config will need amending
    )
  }

  suspend fun migrateEntitiesForPage(context: MigrationContext<MigrationPage<FILTER>>) = getIds(context.body.filter, context.body.pageSize, context.body.pageNumber).takeUnless {
    migrationHistoryService.isCancelling(context.migrationId)
  }?.content?.map {
    MigrationContext(
      context = context,
      body = it,
    )
  }?.forEach { queueService.sendMessageNoTracing(MigrationMessageType.MIGRATE_ENTITY, it) }

  suspend fun migrateStatusCheck(context: MigrationContext<MigrationStatusCheck>) {
    // no need to carry on checking if cancelling
    if (migrationHistoryService.isCancelling(context.migrationId)) return

    /*
       when checking if there are messages to process, it is always an estimation due to SQS, therefore once
       we think there are no messages we check several times in row reducing probability of false positives significantly
     */
    if (queueService.isItProbableThatThereAreStillMessagesToBeProcessed(context.type)) {
      queueService.sendMessage(
        MigrationMessageType.MIGRATE_STATUS_CHECK,
        MigrationContext(
          context = context,
          body = MigrationStatusCheck(),
        ),
        delaySeconds = completeCheckScheduledRetrySeconds,
      )
    } else {
      if (context.body.hasCheckedAReasonableNumberOfTimes(completeCheckCount)) {
        migrationHistoryService.recordMigrationCompleted(
          migrationId = context.migrationId,
          recordsFailed = queueService.countMessagesThatHaveFailed(context.type),
          recordsMigrated = getMigrationCount(context.migrationId),
        )
        telemetryClient.trackEvent(
          "${migrationType.telemetryName}-migration-completed",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "estimatedCount" to context.estimatedCount.toString(),
            "durationMinutes" to context.durationMinutes().toString(),
          ),
          null,
        )
      } else {
        queueService.sendMessage(
          MigrationMessageType.MIGRATE_STATUS_CHECK,
          MigrationContext(
            context = context,
            body = context.body.increment(),
          ),
          delaySeconds = completeCheckRetrySeconds,
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
        MigrationMessageType.CANCEL_MIGRATION,
        MigrationContext(
          context = context,
          body = MigrationStatusCheck(),
        ),
        delaySeconds = completeCheckRetrySeconds * 10,
      )
    } else {
      if (context.body.hasCheckedAReasonableNumberOfTimes(completeCheckCount)) {
        telemetryClient.trackEvent(
          "${migrationType.telemetryName}-migration-cancelled",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "estimatedCount" to context.estimatedCount.toString(),
            "durationMinutes" to context.durationMinutes().toString(),
          ),
          null,
        )
        migrationHistoryService.recordMigrationCancelled(
          migrationId = context.migrationId,
          recordsFailed = queueService.countMessagesThatHaveFailed(context.type),
          recordsMigrated = getMigrationCount(context.migrationId),
        )
      } else {
        queueService.purgeAllMessages(context.type)
        queueService.sendMessage(
          MigrationMessageType.CANCEL_MIGRATION,
          MigrationContext(
            context = context,
            body = context.body.increment(),
          ),
          delaySeconds = completeCheckRetrySeconds,
        )
      }
    }
  }

  suspend fun cancel(migrationId: String) {
    val migration = migrationHistoryService.get(migrationId)
    telemetryClient.trackEvent(
      "${migrationType.telemetryName}-migration-cancel-requested",
      mapOf<String, String>(
        "migrationId" to migration.migrationId,
      ),
      null,
    )
    migrationHistoryService.recordMigrationCancelledRequested(migrationId)
    auditService.sendAuditEvent(
      AuditType.MIGRATION_CANCEL_REQUESTED.name,
      mapOf("migrationType" to migrationType.name, "migrationId" to migration.migrationId),
    )
    queueService.purgeAllMessagesNowAndAgainInTheNearFuture(
      MigrationContext(
        context = MigrationContext(type = migrationType, migrationId, migration.estimatedRecordCount, Unit),
        body = MigrationStatusCheck(),
      ),
      message = MigrationMessageType.CANCEL_MIGRATION,
    )
  }

  suspend fun refresh(migrationId: String) {
    val migration = migrationHistoryService.get(migrationId)
    if (migration.status != MigrationStatus.COMPLETED) {
      throw BadRequestException("Migration $migrationId is not completed")
    }
    migrationHistoryService.recordMigrationCompleted(
      migrationId = migrationId,
      recordsFailed = queueService.countMessagesThatHaveFailed(migration.migrationType),
      recordsMigrated = getMigrationCount(migrationId),
    )
  }

  open suspend fun retryCreateMapping(context: MigrationContext<MAPPING>) {
    mappingService.createMapping(
      context.body,
      object : ParameterizedTypeReference<DuplicateErrorResponse<MAPPING>>() {},
    )
  }
}

fun <T> MigrationContext<T>.durationMinutes(): Long = Duration.between(LocalDateTime.parse(this.migrationId), LocalDateTime.now()).toMinutes()

class MigrationPage<FILTER>(val filter: FILTER, val pageNumber: Long, val pageSize: Long)

data class MigrationStatusCheck(val checkCount: Int = 0) {
  fun hasCheckedAReasonableNumberOfTimes(closeDownCheckCount: Int) = checkCount > closeDownCheckCount
  fun increment() = this.copy(checkCount = checkCount + 1)
}
