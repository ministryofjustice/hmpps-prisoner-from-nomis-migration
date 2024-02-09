package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IncidentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IncidentIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IncidentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AuditService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.toMigrateRequest

@Service
class IncidentsMigrationService(
  queueService: MigrationQueueService,
  private val nomisApiService: NomisApiService,
  migrationHistoryService: MigrationHistoryService,
  telemetryClient: TelemetryClient,
  auditService: AuditService,
  private val incidentsService: IncidentsService,
  private val incidentsMappingService: IncidentsMappingService,
  @Value("\${incidents.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,

) : MigrationService<IncidentsMigrationFilter, IncidentIdResponse, IncidentResponse, IncidentMappingDto>(
  queueService = queueService,
  auditService = auditService,
  migrationHistoryService = migrationHistoryService,
  mappingService = incidentsMappingService,
  telemetryClient = telemetryClient,
  migrationType = MigrationType.INCIDENTS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun getIds(
    migrationFilter: IncidentsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<IncidentIdResponse> {
    return nomisApiService.getIncidentIds(
      fromDate = migrationFilter.fromDate,
      toDate = migrationFilter.toDate,
      pageNumber = pageNumber,
      pageSize = pageSize,
    )
  }

  override suspend fun migrateNomisEntity(context: MigrationContext<IncidentIdResponse>) {
    log.info("attempting to migrate $this")
    val nomisIncidentId = context.body.incidentId
    val migrationId = context.migrationId

    incidentsMappingService.findNomisIncidentMapping(nomisIncidentId)
      ?.run {
        log.info("Will not migrate the incident=$nomisIncidentId since it was already mapped to DPS incident ${this.incidentId} during migration ${this.label}")
      }
      ?: run {
        val nomisIncidentResponse = nomisApiService.getIncident(nomisIncidentId)
        val migratedIncident = incidentsService.migrateIncident(nomisIncidentResponse.toMigrateRequest())
          .also {
            createIncidentMapping(
              incidentId = it.id,
              nomisIncidentId = nomisIncidentId,
              context = context,
            )
          }
        telemetryClient.trackEvent(
          "${MigrationType.INCIDENTS.telemetryName}-migration-entity-migrated",
          mapOf(
            "nomisIncidentId" to nomisIncidentId.toString(),
            "incidentId" to migratedIncident.id,
            "migrationId" to migrationId,
          ),
          null,
        )
      }
  }

  private suspend fun createIncidentMapping(
    nomisIncidentId: Long,
    incidentId: String,
    context: MigrationContext<*>,
  ) = try {
    incidentsMappingService.createMapping(
      IncidentMappingDto(
        nomisIncidentId = nomisIncidentId,
        incidentId = incidentId,
        label = context.migrationId,
        mappingType = IncidentMappingDto.MappingType.MIGRATED,
      ),
      object : ParameterizedTypeReference<DuplicateErrorResponse<IncidentMappingDto>>() {},
    ).also {
      if (it.isError) {
        val duplicateErrorDetails = (it.errorResponse!!).moreInfo
        telemetryClient.trackEvent(
          "nomis-migration-incident-duplicate",
          mapOf<String, String>(
            "migrationId" to context.migrationId,
            "existingNomisIncidentId" to duplicateErrorDetails.existing.nomisIncidentId.toString(),
            "duplicateNomisIncidentId" to duplicateErrorDetails.duplicate.nomisIncidentId.toString(),
            "existingIncidentId" to duplicateErrorDetails.existing.incidentId,
            "duplicateIncidentId" to duplicateErrorDetails.duplicate.incidentId,
            "durationMinutes" to context.durationMinutes().toString(),
          ),
          null,
        )
      }
    }
  } catch (e: Exception) {
    log.error(
      "Failed to create mapping for nomisIncidentId: $nomisIncidentId, incidentId $incidentId",
      e,
    )
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = IncidentMappingDto(
          nomisIncidentId = nomisIncidentId,
          incidentId = incidentId,
          mappingType = IncidentMappingDto.MappingType.MIGRATED,
        ),
      ),
    )
  }
}
