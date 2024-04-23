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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes

@Service
class AlertsMigrationService(
  queueService: MigrationQueueService,
  private val alertsNomisService: AlertsNomisApiService,
  private val alertsMappingService: AlertsMappingApiService,
  private val alertsDpsService: AlertsDpsApiService,
  migrationHistoryService: MigrationHistoryService,
  telemetryClient: TelemetryClient,
  auditService: AuditService,
  @Value("\${alerts.page.size:1000}") pageSize: Long,
  @Value("\${alerts.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${alerts.complete-check.count}") completeCheckCount: Int,
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
    val offenderNo = context.body.offenderNo

    alertsMappingService.getOrNullByNomisId(nomisBookingId, nomisAlertSequence)?.run {
      log.info("Will not migrate the alert since it is migrated already, NOMIS booking id is $nomisBookingId for $offenderNo, sequence is $nomisAlertSequence, DPS Alert id is ${this.dpsAlertId} as part migration ${this.label ?: "NONE"} (${this.mappingType})")
    } ?: run {
      val nomisAlert = alertsNomisService.getAlert(nomisBookingId, nomisAlertSequence)
      alertsDpsService.migrateAlert(nomisAlert.toDPSMigratedAlert(context.body.offenderNo))?.also {
        createMapping(
          nomisBookingId = nomisBookingId,
          nomisAlertSequence = nomisAlertSequence,
          dpsAlertId = it.alertUuid.toString(),
          offenderNo = context.body.offenderNo,
          context = context,
        )
        telemetryClient.trackEvent(
          "alerts-migration-entity-migrated",
          mapOf(
            "nomisBookingId" to nomisBookingId,
            "nomisAlertSequence" to nomisAlertSequence,
            "offenderNo" to offenderNo,
            "migrationId" to context.migrationId,
            "dpsAlertId" to it.alertUuid.toString(),
          ),
        )
      } ?: run {
        telemetryClient.trackEvent(
          "alerts-migration-entity-migration-rejected",
          mapOf(
            "nomisBookingId" to nomisBookingId,
            "nomisAlertSequence" to nomisAlertSequence,
            "offenderNo" to offenderNo,
            "migrationId" to context.migrationId,
          ),
        )
      }
    }
  }

  private suspend fun createMapping(
    nomisBookingId: Long,
    nomisAlertSequence: Long,
    dpsAlertId: String,
    offenderNo: String,
    context: MigrationContext<*>,
  ) = try {
    alertsMappingService.createMapping(
      AlertMappingDto(
        nomisBookingId = nomisBookingId,
        nomisAlertSequence = nomisAlertSequence,
        dpsAlertId = dpsAlertId,
        label = context.migrationId,
        mappingType = AlertMappingDto.MappingType.MIGRATED,
        offenderNo = offenderNo,
      ),
      object : ParameterizedTypeReference<DuplicateErrorResponse<AlertMappingDto>>() {},
    ).also {
      if (it.isError) {
        val duplicateErrorDetails = (it.errorResponse!!).moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-alerts-duplicate",
          mapOf<String, String>(
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
      "Failed to create mapping for alert booking nomis id: $nomisBookingId and sequence $nomisAlertSequence, dps id $dpsAlertId",
      e,
    )
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = AlertMappingDto(
          nomisBookingId = nomisBookingId,
          nomisAlertSequence = nomisAlertSequence,
          dpsAlertId = dpsAlertId,
          label = context.migrationId,
          mappingType = AlertMappingDto.MappingType.MIGRATED,
          offenderNo = offenderNo,
        ),
      ),
    )
  }
}
