package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.ResponseMapping.Component.ATTENDEE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.ResponseMapping.Component.CONTRIBUTORY_FACTOR
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.ResponseMapping.Component.IDENTIFIED_NEED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.ResponseMapping.Component.INTERVIEW
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.ResponseMapping.Component.REVIEW
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPChildMappingDto.MappingType.MIGRATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPFullMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes
import java.time.LocalDateTime

@Service
class CSIPMigrationService(
  private val nomisApiService: CSIPNomisApiService,
  private val csipService: CSIPDpsApiService,
  private val csipMappingService: CSIPMappingService,
  @Value("\${csip.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,

) : MigrationService<CSIPMigrationFilter, CSIPIdResponse, CSIPFullMappingDto>(
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
    val nomisCSIPReportId = context.body.csipId
    val migrationId = context.migrationId

    csipMappingService.getCSIPReportByNomisId(nomisCSIPReportId)
      ?.run {
        log.info("Will not migrate the CSIP $nomisCSIPReportId since it was already mapped to DPS CSIP ${this.dpsCSIPReportId} during migration ${this.label}")
      }
      ?: run {
        val nomisCSIPResponse = nomisApiService.getCSIP(nomisCSIPReportId)
        val migrationLabelAsDateTime = LocalDateTime.parse(migrationId)
        val actionDetails = ActionDetails(actionedAt = migrationLabelAsDateTime, actionedBy = "SYS")
        val syncCsipRequest = nomisCSIPResponse.toDPSSyncRequest(actioned = actionDetails)
        csipService.migrateCSIP(syncCsipRequest)
          .also { syncResponse ->
            // At this point we need to determine all mappings and call the appropriate mapping endpoint
            val dpsCSIPReportId = syncResponse.filterReport().uuid.toString()
            createMapping(
              context,
              CSIPFullMappingDto(
                nomisCSIPReportId = nomisCSIPReportId,
                dpsCSIPReportId = dpsCSIPReportId,
                label = migrationId,
                mappingType = CSIPFullMappingDto.MappingType.MIGRATED,
                attendeeMappings = syncResponse.filterChildMappings(dpsCSIPReportId = dpsCSIPReportId, ATTENDEE, mappingType = MIGRATED, label = migrationId),
                factorMappings = syncResponse.filterChildMappings(dpsCSIPReportId = dpsCSIPReportId, CONTRIBUTORY_FACTOR, mappingType = MIGRATED, label = migrationId),
                interviewMappings = syncResponse.filterChildMappings(dpsCSIPReportId = dpsCSIPReportId, INTERVIEW, mappingType = MIGRATED, label = migrationId),
                planMappings = syncResponse.filterChildMappings(dpsCSIPReportId = dpsCSIPReportId, IDENTIFIED_NEED, mappingType = MIGRATED, label = migrationId),
                reviewMappings = syncResponse.filterChildMappings(dpsCSIPReportId = dpsCSIPReportId, REVIEW, mappingType = MIGRATED, label = migrationId),
              ),
            )
            telemetryClient.trackEvent(
              "${MigrationType.CSIP.telemetryName}-migration-entity-migrated",
              mapOf(
                "nomisCSIPId" to nomisCSIPReportId,
                "dpsCSIPId" to dpsCSIPReportId,
                "migrationId" to migrationId,
              ),
            )
          }
      }
  }

  private suspend fun createMapping(context: MigrationContext<CSIPIdResponse>, fullMappingDto: CSIPFullMappingDto) =
    try {
      csipMappingService.createMapping(fullMappingDto, object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPFullMappingDto>>() {})
        .also {
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
        "Failed to create mapping for nomisCSIPId: ${fullMappingDto.nomisCSIPReportId}, dpsCSIPId ${fullMappingDto.dpsCSIPReportId}" +
          " or one of its children",
        e,
      )
      queueService.sendMessage(
        MigrationMessageType.RETRY_MIGRATION_MAPPING,
        MigrationContext(
          context = context,
          body = CSIPFullMappingDto(
            nomisCSIPReportId = fullMappingDto.nomisCSIPReportId,
            dpsCSIPReportId = fullMappingDto.dpsCSIPReportId,
            mappingType = CSIPFullMappingDto.MappingType.MIGRATED,
            attendeeMappings = listOf(),
            factorMappings = listOf(),
            interviewMappings = listOf(),
            planMappings = listOf(),
            reviewMappings = listOf(),
          ),
        ),
      )
    }
}
