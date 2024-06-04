package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes

@Service
class CSIPMigrationService(
  queueService: MigrationQueueService,
  private val nomisApiService: CSIPNomisApiService,
  migrationHistoryService: MigrationHistoryService,
  telemetryClient: TelemetryClient,
  auditService: AuditService,
  private val csipService: CSIPService,
  private val csipMappingService: CSIPMappingService,
  @Value("\${csip.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,

) : MigrationService<CSIPMigrationFilter, CSIPIdResponse, CSIPResponse, CSIPMappingDto>(
  queueService = queueService,
  auditService = auditService,
  migrationHistoryService = migrationHistoryService,
  mappingService = csipMappingService,
  telemetryClient = telemetryClient,
  migrationType = MigrationType.CSIP,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(
    migrationFilter: CSIPMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<CSIPIdResponse> {
    return nomisApiService.getCSIPIds(
      fromDate = migrationFilter.fromDate,
      toDate = migrationFilter.toDate,
      pageNumber = pageNumber,
      pageSize = pageSize,
    )
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<CSIPIdResponse>) {
    log.info("attempting to migrate $this")
    val nomisCSIPId = context.body.id
    val migrationId = context.migrationId

    csipMappingService.findNomisCSIPMapping(nomisCSIPId)
      ?.run {
        log.info("Will not migrate the CSIP $nomisCSIPId since it was already mapped to DPS CSIP ${this.dpsCSIPId} during migration ${this.label}")
      }
      ?: run {
        val nomisCSIPResponse = nomisApiService.getCSIP(nomisCSIPId)
        val migratedCSIP = csipService.migrateCSIP(nomisCSIPResponse.toMigrateRequest())
          .also {
            createCSIPMapping(
              nomisCSIPId = nomisCSIPId,
              dpsCSIPId = it.dpsCSIPId,
              context = context,
            )
          }
        telemetryClient.trackEvent(
          "${MigrationType.CSIP.telemetryName}-migration-entity-migrated",
          mapOf(
            "nomisCSIPId" to nomisCSIPId.toString(),
            "dpsCSIPId" to migratedCSIP.dpsCSIPId,
            "migrationId" to migrationId,
          ),
          null,
        )
      }
  }

  private suspend fun createCSIPMapping(
    nomisCSIPId: Long,
    dpsCSIPId: String,
    context: MigrationContext<*>,
  ) = try {
    csipMappingService.createMapping(
      CSIPMappingDto(
        nomisCSIPId = nomisCSIPId,
        dpsCSIPId = dpsCSIPId,
        label = context.migrationId,
        mappingType = CSIPMappingDto.MappingType.MIGRATED,
      ),
      object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPMappingDto>>() {},
    ).also {
      if (it.isError) {
        val duplicateErrorDetails = (it.errorResponse!!).moreInfo
        telemetryClient.trackEvent(
          "${MigrationType.CSIP.telemetryName}-nomis-migration-duplicate",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "existingNomisCSIPId" to duplicateErrorDetails.existing.nomisCSIPId.toString(),
            "duplicateNomisCSIPId" to duplicateErrorDetails.duplicate.nomisCSIPId.toString(),
            "existingDPSCSIPId" to duplicateErrorDetails.existing.dpsCSIPId,
            "duplicateDPSCSIPId" to duplicateErrorDetails.duplicate.dpsCSIPId,
            "durationMinutes" to context.durationMinutes().toString(),
          ),
          null,
        )
      }
    }
  } catch (e: Exception) {
    log.error(
      "Failed to create mapping for nomisCSIPId: $nomisCSIPId, dpsCSIPId $dpsCSIPId",
      e,
    )
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = CSIPMappingDto(
          nomisCSIPId = nomisCSIPId,
          dpsCSIPId = dpsCSIPId,
          mappingType = CSIPMappingDto.MappingType.MIGRATED,
        ),
      ),
    )
  }
}
