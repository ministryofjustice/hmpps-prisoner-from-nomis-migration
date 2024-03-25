package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType

@Service
class AlertsMigrationService(
  queueService: MigrationQueueService,
  private val alertsNomisService: AlertsNomisApiService,
  migrationHistoryService: MigrationHistoryService,
  telemetryClient: TelemetryClient,
  auditService: AuditService,
  alertsMappingService: AlertsMappingApiService,
  @Value("\${alerts.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : MigrationService<AlertsMigrationFilter, AlertIdResponse, AlertResponse, AlertMappingDto>(
  queueService = queueService,
  auditService = auditService,
  migrationHistoryService = migrationHistoryService,
  mappingService = alertsMappingService,
  telemetryClient = telemetryClient,
  migrationType = MigrationType.ALERTS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(
    migrationFilter: AlertsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<AlertIdResponse> {
    return alertsNomisService.getAlertIds(
      fromDate = migrationFilter.fromDate,
      toDate = migrationFilter.toDate,
      pageNumber = pageNumber,
      pageSize = pageSize,
    )
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<AlertIdResponse>) {
    log.info("attempting to migrate ${context.body}")
    val nomisBookingId = context.body.bookingId
    val nomisAlertSequence = context.body.alertSequence

    telemetryClient.trackEvent(
      "alerts-migration-entity-migrated",
      mapOf(
        "nomisBookingId" to nomisBookingId,
        "nomisAlertSequence" to nomisAlertSequence,
        "migrationId" to context.migrationId,
      ),
    )
  }
}
