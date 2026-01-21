package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IncidentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.IncidentIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ByPageNumberMigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.durationMinutes

@Service
class IncidentsMigrationService(
  private val nomisApiService: IncidentsNomisApiService,
  private val incidentsService: IncidentsService,
  private val incidentsMappingService: IncidentsMappingService,
  jsonMapper: JsonMapper,
  @Value("\${incidents.page.size:1000}") pageSize: Long,
  @Value("\${complete-check.delay-seconds}") completeCheckDelaySeconds: Int,
  @Value("\${complete-check.count}") completeCheckCount: Int,

) : ByPageNumberMigrationService<IncidentsMigrationFilter, IncidentIdResponse, IncidentMappingDto>(
  mappingService = incidentsMappingService,
  migrationType = MigrationType.INCIDENTS,
  pageSize = pageSize,
  completeCheckDelaySeconds = completeCheckDelaySeconds,
  completeCheckCount = completeCheckCount,
  jsonMapper = jsonMapper,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun getIds(
    migrationFilter: IncidentsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): PageImpl<IncidentIdResponse> = nomisApiService.getIncidentIds(
    fromDate = migrationFilter.fromDate,
    toDate = migrationFilter.toDate,
    pageNumber = pageNumber,
    pageSize = pageSize,
  )

  override suspend fun getPageOfIds(
    migrationFilter: IncidentsMigrationFilter,
    pageSize: Long,
    pageNumber: Long,
  ): List<IncidentIdResponse> = getIds(migrationFilter, pageSize, pageNumber).content

  override suspend fun getTotalNumberOfIds(migrationFilter: IncidentsMigrationFilter): Long = getIds(migrationFilter, 1, 0).totalElements

  override suspend fun migrateNomisEntity(context: MigrationContext<IncidentIdResponse>) {
    log.info("attempting to migrate $this")
    val nomisIncidentId = context.body.incidentId
    val migrationId = context.migrationId

    incidentsMappingService.findByNomisId(nomisIncidentId)
      ?.run {
        log.info("Will not migrate the nomis incident=$nomisIncidentId since it was already mapped to DPS incident ${this.dpsIncidentId} during migration ${this.label}")
      }
      ?: try {
        val nomisIncidentResponse = nomisApiService.getIncident(nomisIncidentId)
        incidentsService.upsertIncident(nomisIncidentResponse.toMigrateUpsertNomisIncident()).also {
          createIncidentMapping(
            dpsIncidentId = it.id.toString(),
            nomisIncidentId = nomisIncidentId,
            context = context,
          )
          telemetryClient.trackEvent(
            "${MigrationType.INCIDENTS.telemetryName}-migration-entity-migrated",
            mapOf(
              "nomisIncidentId" to nomisIncidentId,
              "dpsIncidentId" to it.id,
              "migrationId" to migrationId,
            ),
          )
        }
      } catch (e: WebClientResponseException.Conflict) {
        // We have a conflict - this should only ever happen if the incident was stored in DPS, but we didn't receive a response in time
        // so is never added to the incidents mapping table
        log.error("Conflict received from DPS for nomisIncidentId: $nomisIncidentId, attempting to recover.", e)
        telemetryClient.trackEvent(
          "${MigrationType.INCIDENTS.telemetryName}-migration-entity-migration-conflict",
          mapOf(
            "nomisIncidentId" to nomisIncidentId,
            "reason" to e.toString(),
            "migrationId" to context.migrationId,
          ),
        )
        recoverFromDPSConflict(nomisIncidentId, context)
      } catch (e: Exception) {
        telemetryClient.trackEvent(
          "${MigrationType.INCIDENTS.telemetryName}-migration-entity-migration-failed",
          mapOf(
            "nomisIncidentId" to nomisIncidentId,
            "reason" to e.toString(),
            "migrationId" to migrationId,
          ),
        )
        throw e
      }
  }

  private suspend fun recoverFromDPSConflict(
    nomisIncidentId: Long,
    context: MigrationContext<*>,
  ) {
    incidentsService.getIncidentByNomisId(nomisIncidentId).also {
      createIncidentMapping(
        dpsIncidentId = it.id.toString(),
        nomisIncidentId = nomisIncidentId,
        context = context,
      )
      telemetryClient.trackEvent(
        "${MigrationType.INCIDENTS.telemetryName}-migration-entity-migrated",
        mapOf(
          "nomisIncidentId" to nomisIncidentId,
          "dpsIncidentId" to it.id,
          "migrationId" to context.migrationId,
        ),
      )
    }
  }

  private suspend fun createIncidentMapping(
    nomisIncidentId: Long,
    dpsIncidentId: String,
    context: MigrationContext<*>,
  ) = try {
    incidentsMappingService.createMapping(
      IncidentMappingDto(
        nomisIncidentId = nomisIncidentId,
        dpsIncidentId = dpsIncidentId,
        label = context.migrationId,
        mappingType = IncidentMappingDto.MappingType.MIGRATED,
      ),
      object : ParameterizedTypeReference<DuplicateErrorResponse<IncidentMappingDto>>() {},
    ).also {
      if (it.isError) {
        val duplicateErrorDetails = (it.errorResponse!!).moreInfo
        telemetryClient.trackEvent(
          "${MigrationType.INCIDENTS.telemetryName}-migration-nomis-duplicate",
          mapOf(
            "migrationId" to context.migrationId,
            "existingNomisIncidentId" to duplicateErrorDetails.existing.nomisIncidentId,
            "duplicateNomisIncidentId" to duplicateErrorDetails.duplicate.nomisIncidentId,
            "existingDPSIncidentId" to duplicateErrorDetails.existing.dpsIncidentId,
            "duplicateDPSIncidentId" to duplicateErrorDetails.duplicate.dpsIncidentId,
            "durationMinutes" to context.durationMinutes(),
          ),
        )
      }
    }
  } catch (e: Exception) {
    log.error(
      "Failed to create mapping for nomisIncidentId: $nomisIncidentId, DPS incidentId $dpsIncidentId",
      e,
    )
    queueService.sendMessage(
      MigrationMessageType.RETRY_MIGRATION_MAPPING,
      MigrationContext(
        context = context,
        body = IncidentMappingDto(
          nomisIncidentId = nomisIncidentId,
          dpsIncidentId = dpsIncidentId,
          mappingType = IncidentMappingDto.MappingType.MIGRATED,
        ),
      ),
    )
  }
  override fun parseContextFilter(json: String): MigrationMessage<*, IncidentsMigrationFilter> = jsonMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<IncidentsMigrationFilter, ByPageNumber>> = jsonMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, IncidentIdResponse> = jsonMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, IncidentMappingDto> = jsonMapper.readValue(json)
}
