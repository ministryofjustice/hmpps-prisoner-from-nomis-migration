package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsSynchronisationService.MappingResponse.MAPPING_FAILED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IncidentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType.INCIDENTS

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

  suspend fun synchroniseIncidentUpdate(event: IncidentsOffenderEvent) {
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
      log.debug("Found incident mapping: {}", it)
      log.debug("Sending incident update sync {}", nomisIncident)

      incidentsService.syncIncident(nomisIncident)
      telemetryClient.trackEvent(
        "incident-updated-synchronisation-success",
        event.toTelemetryProperties(it.incidentId),
      )
    } ?: let {
      log.debug("No incident mapping - sending incident sync {} ", nomisIncident)

      incidentsService.syncIncident(nomisIncident).also { incident ->
        tryToCreateIncidentMapping(event, incident.id).also { result ->
          telemetryClient.trackEvent(
            "incident-created-synchronisation-success",
            event.toTelemetryProperties(
              incident.id,
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
      log.debug("Found incident mapping: {}", it)

      incidentsService.deleteIncident(it.incidentId)
      incidentsMappingService.deleteIncidentMapping(it.incidentId)
      telemetryClient.trackEvent(
        "incident-delete-synchronisation-success",
        event.toTelemetryProperties(incidentId = it.incidentId),
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
    incidentId: String,
  ): MappingResponse {
    val mapping = IncidentMappingDto(
      nomisIncidentId = event.incidentCaseId,
      incidentId = incidentId,
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
            "from-nomis-synch-incident-duplicate",
            mapOf<String, String>(
              "existingNomisIncidentId" to duplicateErrorDetails.existing.nomisIncidentId.toString(),
              "duplicateNomisIncidentId" to duplicateErrorDetails.duplicate.nomisIncidentId.toString(),
              "existingIncidentId" to duplicateErrorDetails.existing.incidentId,
              "duplicateIncidentId" to duplicateErrorDetails.duplicate.incidentId,
            ),
            null,
          )
        }
      }
      return MappingResponse.MAPPING_CREATED
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for incident id $incidentId, nomisIncidentId ${event.incidentCaseId}",
        e,
      )
      queueService.sendMessage(
        messageType = RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = INCIDENTS,
        message = mapping,
        telemetryAttributes = event.toTelemetryProperties(incidentId),
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
  incidentId: String? = null,
  mappingFailed: Boolean? = null,
) = mapOf(
  "nomisIncidentId" to "$incidentCaseId",
) + (incidentId?.let { mapOf("incidentId" to it) } ?: emptyMap()) + (
  mappingFailed?.takeIf { it }
    ?.let { mapOf("mapping" to "initial-failure") } ?: emptyMap()
  )
