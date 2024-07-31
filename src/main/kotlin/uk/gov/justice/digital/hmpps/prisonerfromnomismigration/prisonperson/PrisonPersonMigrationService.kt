package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.CreateMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonPersonMigrationMappingRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonPersonMigrationMappingRequest.MigrationType.PHYSICAL_ATTRIBUTES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.PhysicalAttributesMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import java.time.LocalDateTime

@Service
class PrisonPersonMigrationService(
  queueService: MigrationQueueService,
  private val prisonPersonNomisApiService: PrisonPersonNomisApiService,
  private val nomisService: NomisApiService,
  private val prisonPersonMappingService: PrisonPersonMappingApiService,
  private val prisonPersonDpsService: PrisonPersonDpsApiService,
  migrationHistoryService: MigrationHistoryService,
  telemetryClient: TelemetryClient,
  auditService: AuditService,
  @Value("\${page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,
) : MigrationService<PrisonPersonMigrationFilter, PrisonerId, PrisonerPhysicalAttributesResponse, PrisonPersonMigrationMappingRequest>(
  queueService = queueService,
  auditService = auditService,
  migrationHistoryService = migrationHistoryService,
  mappingService = prisonPersonMappingService,
  telemetryClient = telemetryClient,
  migrationType = MigrationType.PRISONPERSON,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {
  override suspend fun getIds(
    migrationFilter: PrisonPersonMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<PrisonerId> =
    if (migrationFilter.prisonerNumber.isNullOrEmpty()) {
      nomisService.getPrisonerIds(
        pageNumber = pageNumber,
        pageSize = pageSize,
      )
    } else {
      // If a single prisoner migration is requested then we must be testing. Pretend that we called nomis-prisoner-api which found a single prisoner.
      PageImpl<PrisonerId>(mutableListOf(PrisonerId(migrationFilter.prisonerNumber)), Pageable.ofSize(1), 1)
    }

  override suspend fun migrateNomisEntity(context: MigrationContext<PrisonerId>) {
    log.info("attempting to migrate ${context.body}")
    val offenderNo = context.body.offenderNo
    val telemetry = mutableMapOf(
      "offenderNo" to offenderNo,
      "migrationId" to context.migrationId,
      "migrationType" to PHYSICAL_ATTRIBUTES,
    )

    try {
      val dpsIds = prisonPersonNomisApiService.getPhysicalAttributes(offenderNo)
        .toDpsMigrationRequest()
        .migrate(offenderNo)
      telemetry["dpsIds"] = dpsIds.toString()

      PrisonPersonMigrationMappingRequest(
        nomisPrisonerNumber = offenderNo,
        migrationType = PHYSICAL_ATTRIBUTES,
        label = context.migrationId,
        dpsIds = dpsIds,
      ).createActivityMapping(context)

      telemetryClient.trackEvent("prisonperson-migration-entity-migrated", telemetry)
    } catch (e: Exception) {
      telemetry["error"] = e.message ?: "unknown error"
      telemetryClient.trackEvent("prisonperson-migration-entity-failed", telemetry)
      throw e
    }
  }

  private suspend fun PrisonerPhysicalAttributesResponse.toDpsMigrationRequest() = bookings.flatMap { booking ->
    booking.physicalAttributes.map { pa ->
      val (lastModifiedAt, lastModifiedBy) = pa.lastModified()
      prisonPersonDpsService.migratePhysicalAttributesRequest(
        heightCentimetres = pa.heightCentimetres,
        weightKilograms = pa.weightKilograms,
        appliesFrom = booking.startDateTime.toLocalDateTime(),
        appliesTo = booking.endDateTime?.toLocalDateTime(),
        createdAt = lastModifiedAt,
        createdBy = lastModifiedBy,
      )
    }
  }

  private suspend fun List<PhysicalAttributesMigrationRequest>.migrate(
    offenderNo: String,
  ) = prisonPersonDpsService.migratePhysicalAttributes(offenderNo, this).fieldHistoryInserted

  private suspend fun PrisonPersonMigrationMappingRequest.createActivityMapping(context: MigrationContext<*>) =
    try {
      prisonPersonMappingService.createMapping(this, object : ParameterizedTypeReference<DuplicateErrorResponse<PrisonPersonMigrationMappingRequest>>() {})
        .also { it.handleError(context) }
    } catch (e: Exception) {
      log.error(
        "Failed to create activity mapping for nomisPrisonerNumber: $nomisPrisonerNumber, dpsIds $dpsIds for migration ${this.label}",
        e,
      )
      queueService.sendMessage(
        MigrationMessageType.RETRY_MIGRATION_MAPPING,
        MigrationContext(
          context = context,
          body = this,
        ),
      )
    }

  private suspend fun CreateMappingResult<PrisonPersonMigrationMappingRequest>.handleError(context: MigrationContext<*>) =
    takeIf { it.isError }
      ?.let { it.errorResponse?.moreInfo }
      ?.also {
        telemetryClient.trackEvent(
          "prisonperson-nomis-migration-duplicate",
          mapOf(
            "migrationId" to context.migrationId,
            "duplicateNomisPrisonerNumber" to it.duplicate.nomisPrisonerNumber,
            "duplicateDpsIds" to it.duplicate.dpsIds.toString(),
            "existingNomisPrisonerNumber" to it.existing.nomisPrisonerNumber,
            "existingDpsIds" to it.existing.dpsIds.toString(),
          ),
          null,
        )
      }

  private fun PhysicalAttributesResponse.lastModified(): Pair<LocalDateTime, String> =
    (modifiedDateTime ?: createDateTime).toLocalDateTime() to (modifiedBy ?: createdBy)

  private fun String.toLocalDateTime() = LocalDateTime.parse(this)

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
}
