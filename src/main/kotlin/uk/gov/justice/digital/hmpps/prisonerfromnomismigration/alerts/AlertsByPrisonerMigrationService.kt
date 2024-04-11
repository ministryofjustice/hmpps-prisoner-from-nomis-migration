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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType

@Service
class AlertsByPrisonerMigrationService(
  queueService: MigrationQueueService,
  private val alertsNomisService: AlertsNomisApiService,
  alertsMappingService: AlertsByPrisonerMigrationMappingApiService,
  private val alertsDpsService: AlertsDpsApiService,
  migrationHistoryService: MigrationHistoryService,
  telemetryClient: TelemetryClient,
  auditService: AuditService,
  @Value("\${alerts.page.size:1000}") pageSize: Long,
  @Value("\${alerts.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${alerts.complete-check.count}") completeCheckCount: Int,
) : MigrationService<AlertsMigrationFilter, PrisonerId, AlertsForPrisonerResponse, List<AlertMappingDto>>(
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
  ): PageImpl<PrisonerId> {
    return alertsNomisService.getPrisonerIds(
      pageNumber = pageNumber,
      pageSize = pageSize,
    )
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonerId>) {
    log.info("attempting to migrate ${context.body}")
    val latestNomisBookingId = context.body.bookingId
    val offenderNo = context.body.offenderNo
    val status = context.body.status

    val nomisAlerts = alertsNomisService.getAlertsToMigrate(offenderNo)
    val activeAlertsFromPreviousBookings = nomisAlerts.previousBookingsAlerts.filter { it.isActive }
    alertsDpsService.migrateAlerts(nomisAlerts.latestBookingAlerts.map { it.toDPSMigratedAlert(context.body.offenderNo) })?.also {
      // TODO create mappings here
      telemetryClient.trackEvent(
        "alerts-migration-entity-migrated",
        mapOf(
          "latestNomisBookingId" to latestNomisBookingId,
          "offenderNo" to offenderNo,
          "status" to status,
          "migrationId" to context.migrationId,
          "alertCount" to it.size.toString(),
          "alertsFromCurrentBooking" to nomisAlerts.latestBookingAlerts.size.toString(),
          "totalAlertsFromPreviousBookings" to nomisAlerts.previousBookingsAlerts.size.toString(),
          "totalActiveAlertsFromPreviousBookings" to activeAlertsFromPreviousBookings.size.toString(),
        ),
      )

      if (activeAlertsFromPreviousBookings.isNotEmpty()) {
        log.debug("active previous alert codes are: ${activeAlertsFromPreviousBookings.joinToString { it.alertCode.code }}")
      }
    } ?: run {
      telemetryClient.trackEvent(
        "alerts-migration-entity-migration-rejected",
        mapOf(
          "latestNomisBookingId" to latestNomisBookingId,
          "offenderNo" to offenderNo,
          "migrationId" to context.migrationId,
        ),
      )
    }
  }
}
