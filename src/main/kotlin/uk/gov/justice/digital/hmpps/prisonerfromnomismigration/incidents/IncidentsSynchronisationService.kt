package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.originatesInDps
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsSynchronisationService.MappingResponse.MAPPING_FAILED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IncidentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType.INCIDENTS
import java.util.UUID

@Service
class IncidentsSynchronisationService(
  private val nomisApiService: NomisApiService,
  private val incidentsNomisApiService: IncidentsNomisApiService,
  private val incidentsMappingService: IncidentsMappingService,
  private val incidentsService: IncidentsService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun synchroniseIncidentInsert(event: IncidentsOffenderEvent) {
    // two way sync so don't want to send an event to DPS that originated there
    if (event.originatesInDps()) {
      telemetryClient.trackEvent("incidents-synchronisation-skipped", event.toTelemetryProperties())
      return
    }
    val nomisIncidentId = event.incidentCaseId
    val nomisIncident = incidentsNomisApiService.getIncident(nomisIncidentId)
    // If DPS is in charge of incidents then don't want to sync NOMIS -> DPS
    if (nomisApiService.isAgencySwitchOnForAgency("INCIDENTS", nomisIncident.agency.code)) {
      telemetryClient.trackEvent(
        "incidents-synchronisation-agency-skipped",
        event.toTelemetryProperties() + ("location" to nomisIncident.agency.code),
      )
      return
    }

    incidentsMappingService.findByNomisId(nomisIncidentId = nomisIncidentId)?.let {
      // Will happen if we try to process the same event again for some reason
      telemetryClient.trackEvent("incidents-synchronisation-created-ignored", event.toTelemetryProperties())
    }
      ?: try {
        incidentsService.upsertIncident(
          NomisSyncRequest(
            id = null,
            initialMigration = false,
            incidentReport = nomisIncident.toNomisIncidentReport(),
          ),
        ).also { dpsIncident ->
          tryToCreateIncidentMapping(event, dpsIncident.id.toString()).also { result ->
            telemetryClient.trackEvent(
              "incidents-synchronisation-created-success",
              event.toTelemetryProperties(
                dpsIncident.id.toString(),
                result == MAPPING_FAILED,
              ),
            )
          }
        }
      } catch (e: WebClientResponseException.Conflict) {
        // We have a conflict - this should only ever happen if the incident was stored in DPS, but we didn't receive a
        // response in time so is never added to the incidents mapping table
        log.error("Conflict received from DPS for nomisIncidentId: $nomisIncidentId, attempting to recover.", e)
        telemetryClient.trackEvent(
          "incidents-synchronisation-created-conflict",
          mapOf(
            "nomisIncidentId" to nomisIncidentId,
            "reason" to e.toString(),
          ),
        )
        recoverFromDPSConflict(nomisIncidentId)
      } catch (e: Exception) {
        telemetryClient.trackEvent(
          "incidents-synchronisation-created-failed",
          mapOf(
            "nomisIncidentId" to nomisIncidentId,
            "reason" to e.toString(),
          ),
        )
        throw e
      }
  }

  private suspend fun recoverFromDPSConflict(
    nomisIncidentId: Long,
  ) {
    incidentsService.getIncidentByNomisId(nomisIncidentId).also {
      tryToCreateIncidentMapping(event = IncidentsOffenderEvent(nomisIncidentId, "DPS_SYNCHRONISATION"), dpsIncidentId = it.id.toString()).also { result ->
        telemetryClient.trackEvent(
          "incidents-synchronisation-created-conflict-recovered",
          mapOf(
            "nomisIncidentId" to nomisIncidentId,
            "dpsIncidentId" to it.id,
          ),
        )
      }
    }
  }

  suspend fun synchroniseIncidentUpdate(event: IncidentsOffenderEvent) {
    // two way sync so don't want to send an event to DPS that originated there
    if (event.originatesInDps()) {
      telemetryClient.trackEvent("incidents-synchronisation-skipped", event.toTelemetryProperties())
      return
    }
    incidentsNomisApiService.getIncidentOrNull(event.incidentCaseId)?.let { nomisIncident ->
      // If DPS is in charge of incidents then don't want to sync NOMIS -> DPS
      if (nomisApiService.isAgencySwitchOnForAgency("INCIDENTS", nomisIncident.agency.code)) {
        telemetryClient.trackEvent(
          "incidents-synchronisation-agency-skipped",
          event.toTelemetryProperties() + ("location" to nomisIncident.agency.code),
        )
        return
      }

      incidentsMappingService.findByNomisId(
        nomisIncidentId = event.incidentCaseId,
      )?.let {
        // For an update - this is the happy path - the mapping exists
        incidentsService.upsertIncident(
          NomisSyncRequest(
            id = UUID.fromString(it.dpsIncidentId),
            initialMigration = false,
            incidentReport = nomisIncident.toNomisIncidentReport(),
          ),
        )
        telemetryClient.trackEvent(
          "incidents-synchronisation-updated-success",
          event.toTelemetryProperties(it.dpsIncidentId),
        )
      } ?: let {
        // The mapping does not exist, fail gracefully
        telemetryClient.trackEvent(
          "incidents-synchronisation-updated-failed",
          event.toTelemetryProperties(),
        )
        throw IllegalStateException("Received UPDATED event for incident that does not exist in mapping table")
      }
    } ?: let {
      telemetryClient.trackEvent(
        "incidents-synchronisation-updated-failed",
        event.toTelemetryProperties(),
      )
      throw IllegalStateException("Received UPDATED event for incident that does not exist in Nomis")
    }
  }

  suspend fun synchroniseIncidentUpdateDelete(event: IncidentsOffenderEvent) {
    // two way sync so don't want to send an event to DPS that originated there
    if (event.originatesInDps()) {
      telemetryClient.trackEvent("incidents-synchronisation-skipped", event.toTelemetryProperties())
      return
    }

    incidentsNomisApiService.getIncidentOrNull(event.incidentCaseId)?.let { nomisIncident ->
      // If DPS is in charge of incidents then don't want to sync NOMIS -> DPS
      if (nomisApiService.isAgencySwitchOnForAgency("INCIDENTS", nomisIncident.agency.code)) {
        telemetryClient.trackEvent(
          "incidents-synchronisation-agency-skipped",
          event.toTelemetryProperties() + ("location" to nomisIncident.agency.code),
        )
        return
      }

      incidentsMappingService.findByNomisId(
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
          "incidents-synchronisation-deleted-child-success",
          event.toTelemetryProperties(it.dpsIncidentId),
        )
      } ?: let {
        telemetryClient.trackEvent(
          "incidents-synchronisation-deleted-child-failed",
          event.toTelemetryProperties(),
        )
        throw IllegalStateException("Received DELETED event for incident that does not exist in mapping table")
      }
    } ?: let {
      // This can happen if the incident has been deleted in NOMIS but the message for a child table is still in
      // the queue due to the delay in the queue processing.
      // We can ignore this as the top level incident has been deleted
      telemetryClient.trackEvent(
        "incidents-synchronisation-deleted-child-failed",
        event.toTelemetryProperties(),
      )
    }
  }

  suspend fun synchroniseIncidentDelete(event: IncidentsOffenderEvent) {
    // two way sync so don't want to send an event to DPS that originated there
    if (event.originatesInDps()) {
      telemetryClient.trackEvent(
        "incidents-synchronisation-deleted-skipped",
        event.toTelemetryProperties(),
      )
      return
    }

    incidentsMappingService.findByNomisId(
      nomisIncidentId = event.incidentCaseId,
    )?.let {
      incidentsService.deleteIncident(it.dpsIncidentId)
      incidentsMappingService.deleteIncidentMapping(it.dpsIncidentId)
      telemetryClient.trackEvent(
        "incidents-synchronisation-deleted-success",
        event.toTelemetryProperties(dpsIncidentId = it.dpsIncidentId),
      )
    } ?: let {
      telemetryClient.trackEvent(
        "incidents-synchronisation-deleted-ignored",
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
            "incidents-synchronisation-nomis-duplicate",
            mapOf(
              "existingNomisIncidentId" to duplicateErrorDetails.existing.nomisIncidentId,
              "duplicateNomisIncidentId" to duplicateErrorDetails.duplicate.nomisIncidentId,
              "existingDPSIncidentId" to duplicateErrorDetails.existing.dpsIncidentId,
              "duplicateDPSIncidentId" to duplicateErrorDetails.duplicate.dpsIncidentId,
            ),
          )
        }
      }
      return MappingResponse.MAPPING_CREATED
    } catch (e: Exception) {
      log.info(
        "Failed to create mapping for dpsIncidentId $dpsIncidentId, nomisIncidentId ${event.incidentCaseId}",
        e.message,
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
        "incidents-synchronisation-mapping-created-success",
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
