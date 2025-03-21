package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.BadRequestException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType

@Service
class MigrateService(
  private val migrationHistoryService: MigrationHistoryService,
  private val telemetryClient: TelemetryClient,
  private val auditService: AuditService,
  private val queueService: MigrationQueueService,
  private val generalMappingService: GeneralMappingService,
) {
  suspend fun cancel(migrationId: String) {
    val migration = migrationHistoryService.get(migrationId)
    telemetryClient.trackEvent(
      "${migration.migrationType.telemetryName}-migration-cancel-requested",
      mapOf<String, String>(
        "migrationId" to migration.migrationId,
      ),
      null,
    )
    migrationHistoryService.recordMigrationCancelledRequested(migrationId)
    auditService.sendAuditEvent(
      AuditType.MIGRATION_CANCEL_REQUESTED.name,
      mapOf("migrationType" to migration.migrationType.name, "migrationId" to migration.migrationId),
    )
    queueService.purgeAllMessagesNowAndAgainInTheNearFuture(
      MigrationContext(
        context = MigrationContext(type = migration.migrationType, migrationId, migration.estimatedRecordCount, Unit),
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
      recordsMigrated = generalMappingService.getMigrationCount(migrationId, migration.migrationType),
    )
  }
}
