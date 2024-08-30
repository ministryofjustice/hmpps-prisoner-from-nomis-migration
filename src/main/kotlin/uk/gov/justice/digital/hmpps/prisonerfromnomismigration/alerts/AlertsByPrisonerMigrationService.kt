package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AlertMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerAlertMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerAlertsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes

@Service
class AlertsByPrisonerMigrationService(
  private val alertsNomisService: AlertsNomisApiService,
  private val nomisService: NomisApiService,
  private val alertsMappingService: AlertsByPrisonerMigrationMappingApiService,
  private val alertsDpsService: AlertsDpsApiService,
  @Value("\${alerts.page.size:1000}") pageSize: Long,
  @Value("\${alerts.complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${alerts.complete-check.count}") completeCheckCount: Int,
) : MigrationService<AlertsMigrationFilter, PrisonerId, AlertMigrationMapping>(
  mappingService = alertsMappingService,
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
  ): PageImpl<PrisonerId> = nomisService.getPrisonerIds(
    pageNumber = pageNumber,
    pageSize = pageSize,
  )

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonerId>) {
    log.info("attempting to migrate ${context.body}")
    val offenderNo = context.body.offenderNo

    // when NOMIS does not find a booking this will be null so just migrate as if there is no alerts
    val nomisAlerts = alertsNomisService.getAlertsToMigrate(offenderNo) ?: PrisonerAlertsResponse(emptyList())
    val alertsToMigrate = nomisAlerts.latestBookingAlerts.map { it.toDPSMigratedAlert() }
    alertsDpsService.migrateAlerts(
      offenderNo = offenderNo,
      alerts = alertsToMigrate,
    ).also {
      createMapping(
        offenderNo = offenderNo,
        PrisonerAlertMappingsDto(
          label = context.migrationId,
          mappingType = PrisonerAlertMappingsDto.MappingType.MIGRATED,
          mappings = it.map { dpsAlert ->
            AlertMappingIdDto(
              nomisBookingId = dpsAlert.offenderBookId,
              nomisAlertSequence = dpsAlert.alertSeq.toLong(),
              dpsAlertId = dpsAlert.alertUuid.toString(),
            )
          },
        ),
        context = context,
      )
      telemetryClient.trackEvent(
        "alerts-migration-entity-migrated",
        mapOf(
          "offenderNo" to offenderNo,
          "migrationId" to context.migrationId,
          "alertCount" to it.size.toString(),
        ),
      )
    }
  }

  private suspend fun createMapping(
    offenderNo: String,
    prisonerMappings: PrisonerAlertMappingsDto,
    context: MigrationContext<PrisonerId>,
  ) = try {
    alertsMappingService.createMapping(
      offenderNo,
      prisonerMappings,
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
        body = AlertMigrationMapping(prisonerMappings, offenderNo = offenderNo),
      ),
    )
  }

  override suspend fun retryCreateMapping(context: MigrationContext<AlertMigrationMapping>) {
    alertsMappingService.createMapping(
      context.body.offenderNo,
      context.body.prisonerMappings,
      object : ParameterizedTypeReference<DuplicateErrorResponse<AlertMappingDto>>() {},
    )
  }
}

data class AlertMigrationMapping(
  val prisonerMappings: PrisonerAlertMappingsDto,
  val offenderNo: String,
)
