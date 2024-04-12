package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes

@Service
class AlertsByPrisonerMigrationService(
  queueService: MigrationQueueService,
  private val alertsNomisService: AlertsNomisApiService,
  private val alertsMappingService: AlertsByPrisonerMigrationMappingApiService,
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
    // TODO - API does not return the actual booking sequence so fake it for more (we know latest is always 1)
    val allNomisAlerts = nomisAlerts.latestBookingAlerts.map { it.toDPSMigratedAlert(true) } +
      nomisAlerts.previousBookingsAlerts.map { it.toDPSMigratedAlert(false) }
    alertsDpsService.migrateAlerts(
      offenderNo = offenderNo,
      alerts = allNomisAlerts,
    ).also {
      createMapping(
        // TODO - DPS is not echoing back alert noms keys so assume the same order but get DPS changed
        // so it returns these values
        it.zip(allNomisAlerts).map { dpsAndNomisAlert ->
          AlertMappingDto(
            nomisBookingId = dpsAndNomisAlert.second.offenderBookId,
            nomisAlertSequence = dpsAndNomisAlert.second.alertSeq.toLong(),
            dpsAlertId = dpsAndNomisAlert.first.alertUuid.toString(),
            label = context.migrationId,
            mappingType = AlertMappingDto.MappingType.MIGRATED,
          )
        },
        context = context,
      )
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
        log.debug("active previous alert codes are: ${activeAlertsFromPreviousBookings.joinToString { alert -> alert.alertCode.code }}")
      }
    }
  }

  private suspend fun createMapping(
    mappings: List<AlertMappingDto>,
    context: MigrationContext<PrisonerId>,
  ) = try {
    alertsMappingService.createMapping(
      mappings,
      object : ParameterizedTypeReference<DuplicateErrorResponse<AlertMappingDto>>() {},
    ).also {
      if (it.isError) {
        val duplicateErrorDetails = (it.errorResponse!!).moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-alerts-duplicate",
          mapOf<String, String>(
            "offenderNo" to context.body.offenderNo,
            "migrationId" to context.migrationId,
            "duplicateDpsAlertId" to duplicateErrorDetails.duplicate.dpsAlertId,
            "duplicateNomisBookingId" to duplicateErrorDetails.duplicate.nomisBookingId.toString(),
            "duplicateNomisAlertSequence" to duplicateErrorDetails.duplicate.nomisAlertSequence.toString(),
            "existingDpsAlertId" to duplicateErrorDetails.existing.dpsAlertId,
            "existingNomisBookingId" to duplicateErrorDetails.existing.nomisBookingId.toString(),
            "existingNomisAlertSequence" to duplicateErrorDetails.existing.nomisAlertSequence.toString(),
            "durationMinutes" to context.durationMinutes().toString(),
          ),
          null,
        )
      }
    }
  } catch (e: Exception) {
    log.error(
      "Failed to create mapping for alerts for prisoner ${context.body.offenderNo}",
      e,
    )
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = mappings,
      ),
    )
  }

  override suspend fun retryCreateMapping(context: MigrationContext<List<AlertMappingDto>>) {
    alertsMappingService.createMapping(
      context.body,
      object : ParameterizedTypeReference<DuplicateErrorResponse<AlertMappingDto>>() {},
    )
  }
}
