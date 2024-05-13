package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsSynchronisationService.MappingResponse.MAPPING_FAILED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IncidentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType.INCIDENTS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.toNomisIncidentReport
import java.util.UUID

@Service
class IncidentsSynchronisationService(
  private val nomisApiService: NomisApiService,
  private val incidentsMappingService: IncidentsMappingService,
  private val incidentsService: IncidentsService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun synchroniseIncidentUpsert(event: IncidentsOffenderEvent) {
    // Should never happen
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "incident-synchronisation-skipped",
        event.toTelemetryProperties(),
      )
      return
    }

    val nomisIncident = nomisApiService.getIncident(event.incidentCaseId)
    incidentsMappingService.findNomisIncidentMapping(
      nomisIncidentId = event.incidentCaseId,
    )?.let {
      incidentsService.upsertIncident(
        NomisSyncRequest(
          id = UUID.fromString(it.dpsIncidentId),
          initialMigration = false,
          incidentReport = nomisIncident.toNomisIncidentReport(),
        ),
      )
      telemetryClient.trackEvent(
        "incident-updated-synchronisation-success",
        event.toTelemetryProperties(it.dpsIncidentId),
      )
    } ?: let {
      incidentsService.upsertIncident(
        NomisSyncRequest(
          initialMigration = false,
          incidentReport = nomisIncident.toNomisIncidentReport(),
        ),
      ).also { incident ->
        tryToCreateIncidentMapping(event, incident.id.toString()).also { result ->
          telemetryClient.trackEvent(
            "incident-created-synchronisation-success",
            event.toTelemetryProperties(
              incident.id.toString(),
              result == MAPPING_FAILED,
            ),
          )
        }
      }
    }
  }

  suspend fun synchroniseIncidentDelete(event: IncidentsOffenderEvent) {
    // Should never happen
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "incident-delete-synchronisation-skipped",
        event.toTelemetryProperties(),
      )
      return
    }

    incidentsMappingService.findNomisIncidentMapping(
      nomisIncidentId = event.incidentCaseId,
    )?.let {
      incidentsService.deleteIncident(it.dpsIncidentId)
      incidentsMappingService.deleteIncidentMapping(it.dpsIncidentId)
      telemetryClient.trackEvent(
        "incident-delete-synchronisation-success",
        event.toTelemetryProperties(dpsIncidentId = it.dpsIncidentId),
      )
    } ?: let {
      telemetryClient.trackEvent(
        "incident-delete-synchronisation-ignored",
        event.toTelemetryProperties(),
      )
    }
  }

  enum class MappingResponse {
    MAPPING_CREATED,
    MAPPING_FAILED,
  }

  suspend fun tryToCreateIncidentMapping(
    event: IncidentsOffenderEvent,
    dpsIncidentId: String,
  ): MappingResponse {
    val mapping = IncidentMappingDto(
      nomisIncidentId = event.incidentCaseId,
      dpsIncidentId = dpsIncidentId,
      mappingType = IncidentMappingDto.MappingType.NOMIS_CREATED,
    )
    try {
      incidentsMappingService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<IncidentMappingDto>>() {},
      ).also {
        if (it.isError) {
          val duplicateErrorDetails = (it.errorResponse!!).moreInfo
          telemetryClient.trackEvent(
            "from-nomis-sync-incident-duplicate",
            mapOf<String, String>(
              "existingNomisIncidentId" to duplicateErrorDetails.existing.nomisIncidentId.toString(),
              "duplicateNomisIncidentId" to duplicateErrorDetails.duplicate.nomisIncidentId.toString(),
              "existingDPSIncidentId" to duplicateErrorDetails.existing.dpsIncidentId,
              "duplicateDPSIncidentId" to duplicateErrorDetails.duplicate.dpsIncidentId,
            ),
            null,
          )
        }
      }
      return MappingResponse.MAPPING_CREATED
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for dpsIncidentId $dpsIncidentId, nomisIncidentId ${event.incidentCaseId}",
        e,
      )
      queueService.sendMessage(
        messageType = RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = INCIDENTS,
        message = mapping,
        telemetryAttributes = event.toTelemetryProperties(dpsIncidentId),
      )
      return MAPPING_FAILED
    }
  }

  suspend fun retryCreateIncidentMapping(retryMessage: InternalMessage<IncidentMappingDto>) {
    incidentsMappingService.createMapping(
      retryMessage.body,
      object : ParameterizedTypeReference<DuplicateErrorResponse<IncidentMappingDto>>() {},
    ).also {
      telemetryClient.trackEvent(
        "incident-mapping-created-synchronisation-success",
        retryMessage.telemetryAttributes,
      )
    }
  }
}

private fun IncidentsOffenderEvent.toTelemetryProperties(
  dpsIncidentId: String? = null,
  mappingFailed: Boolean? = null,
) = mapOf(
  "nomisIncidentId" to "$incidentCaseId",
) + (dpsIncidentId?.let { mapOf("dpsIncidentId" to it) } ?: emptyMap()) + (
  mappingFailed?.takeIf { it }
    ?.let { mapOf("mapping" to "initial-failure") } ?: emptyMap()
  )
