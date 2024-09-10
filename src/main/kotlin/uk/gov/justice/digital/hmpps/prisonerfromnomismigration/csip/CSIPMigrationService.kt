package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.ResponseMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPReportMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes

@Service
class CSIPMigrationService(
  private val nomisApiService: CSIPNomisApiService,
  private val csipService: CSIPDpsApiService,
  private val csipMappingService: CSIPMappingService,
  @Value("\${csip.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,

) : MigrationService<CSIPMigrationFilter, CSIPIdResponse, CSIPReportMappingDto>(
  mappingService = csipMappingService,
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
  ): PageImpl<CSIPIdResponse> = nomisApiService.getCSIPIds(
    fromDate = migrationFilter.fromDate,
    toDate = migrationFilter.toDate,
    pageNumber = pageNumber,
    pageSize = pageSize,
  )

  override suspend fun migrateNomisEntity(context: MigrationContext<CSIPIdResponse>) {
    log.info("attempting to migrate $this")
    val nomisCSIPId = context.body.csipId
    val migrationId = context.migrationId

    csipMappingService.getCSIPReportByNomisId(nomisCSIPId)
      ?.run {
        log.info("Will not migrate the CSIP $nomisCSIPId since it was already mapped to DPS CSIP ${this.dpsCSIPReportId} during migration ${this.label}")
      }
      ?: run {
        val nomisCSIPResponse = nomisApiService.getCSIP(nomisCSIPId)
        csipService.migrateCSIP(nomisCSIPResponse.toDPSSyncRequest(actioned = nomisCSIPResponse.toActionDetails()))
          .also { migratedCSIP ->
            // At this point we need to determine all mappings and call the appropriate mapping endpoint
            val dpsCSIPReportId = migratedCSIP.mappings.first { it.component == ResponseMapping.Component.RECORD }.uuid.toString()
            createCSIPReportMapping(nomisCSIPId = nomisCSIPId, dpsCSIPId = dpsCSIPReportId, context = context)

            // TODO **** MAP FACTORS ****
            // TODO **** MAP ATTENDEES ****
            // TODO **** MAP INTERVIEWS ****
            // TODO **** MAP PLANS ****
            // TODO **** MAP REVIEWS ****

            telemetryClient.trackEvent(
              "${MigrationType.CSIP.telemetryName}-migration-entity-migrated",
              mapOf(
                "nomisCSIPId" to nomisCSIPId,
                "dpsCSIPId" to dpsCSIPReportId,
                "migrationId" to migrationId,
              ),
            )
          }
      }
  }

  private suspend fun createCSIPReportMapping(
    nomisCSIPId: Long,
    dpsCSIPId: String,
    context: MigrationContext<*>,
  ) = try {
    csipMappingService.createMapping(
      CSIPReportMappingDto(
        nomisCSIPReportId = nomisCSIPId,
        dpsCSIPReportId = dpsCSIPId,
        label = context.migrationId,
        mappingType = CSIPReportMappingDto.MappingType.MIGRATED,
      ),
      object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPReportMappingDto>>() {},
    ).also {
      if (it.isError) {
        val duplicateErrorDetails = (it.errorResponse!!).moreInfo
        telemetryClient.trackEvent(
          "${MigrationType.CSIP.telemetryName}-nomis-migration-duplicate",
          mapOf(
            "migrationId" to context.migrationId,
            "existingNomisCSIPId" to duplicateErrorDetails.existing.nomisCSIPReportId,
            "duplicateNomisCSIPId" to duplicateErrorDetails.duplicate.nomisCSIPReportId,
            "existingDPSCSIPId" to duplicateErrorDetails.existing.dpsCSIPReportId,
            "duplicateDPSCSIPId" to duplicateErrorDetails.duplicate.dpsCSIPReportId,
            "durationMinutes" to context.durationMinutes(),
          ),
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
        body = CSIPReportMappingDto(
          nomisCSIPReportId = nomisCSIPId,
          dpsCSIPReportId = dpsCSIPId,
          mappingType = CSIPReportMappingDto.MappingType.MIGRATED,
        ),
      ),
    )
  }
}
