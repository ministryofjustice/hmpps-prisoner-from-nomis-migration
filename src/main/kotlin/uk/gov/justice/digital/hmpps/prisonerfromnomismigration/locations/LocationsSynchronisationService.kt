package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.LocationsSynchronisationService.MappingResponse.MAPPING_FAILED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType.LOCATIONS
import java.util.*

@Service
class LocationsSynchronisationService(
  private val nomisApiService: NomisApiService,
  private val locationsMappingService: LocationsMappingService,
  private val locationsService: LocationsService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun synchroniseLocationCreateOrUpdate(event: LocationsOffenderEvent) {
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "location-synchronisation-skipped",
        event.toTelemetryProperties(),
      )
      return
    }

    val nomisLocation = nomisApiService.getLocation(event.internalLocationId)
    locationsMappingService.getMappingGivenNomisId(event.internalLocationId)?.let {
      val upsertSyncRequest = toUpsertSyncRequest(UUID.fromString(it.dpsLocationId), nomisLocation)
      log.debug("Found location mapping: {}, sending location upsert sync {}", it, upsertSyncRequest)

      locationsService.upsertLocation(upsertSyncRequest)
      telemetryClient.trackEvent(
        "location-updated-synchronisation-success",
        event.toTelemetryProperties(it.dpsLocationId),
      )
    } ?: let {
      val upsertSyncRequest = toUpsertSyncRequest(nomisLocation)
      log.debug("No location mapping - sending location upsert sync {} ", upsertSyncRequest)

      locationsService.upsertLocation(upsertSyncRequest).also { location ->
        tryToCreateLocationMapping(event, location.id.toString()).also { result ->
          telemetryClient.trackEvent(
            "location-created-synchronisation-success",
            event.toTelemetryProperties(
              location.id.toString(),
              result == MAPPING_FAILED,
            ),
          )
        }
      }
    }
  }

  enum class MappingResponse {
    MAPPING_CREATED,
    MAPPING_FAILED,
  }

  suspend fun tryToCreateLocationMapping(
    event: LocationsOffenderEvent,
    locationId: String,
  ): MappingResponse {
    val mapping = LocationMappingDto(
      dpsLocationId = locationId,
      nomisLocationId = event.internalLocationId,
      mappingType = NOMIS_CREATED,
    )
    try {
      locationsMappingService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<LocationMappingDto>>() {},
      ).also {
        if (it.isError) {
          val duplicateErrorDetails = (it.errorResponse!!).moreInfo
          telemetryClient.trackEvent(
            "from-nomis-synch-location-duplicate",
            mapOf<String, String>(
              "duplicateDpsLocationId" to duplicateErrorDetails.duplicate.dpsLocationId,
              "duplicateNomisLocationId" to duplicateErrorDetails.duplicate.nomisLocationId.toString(),
              "existingDpsLocationId" to duplicateErrorDetails.existing.dpsLocationId,
              "existingNomisLocationId" to duplicateErrorDetails.existing.nomisLocationId.toString(),
            ),
            null,
          )
        }
      }
      return MappingResponse.MAPPING_CREATED
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for dpsLocation id $locationId, nomisLocationId ${event.internalLocationId}",
        e,
      )
      queueService.sendMessage(
        messageType = RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = LOCATIONS,
        message = mapping,
        telemetryAttributes = event.toTelemetryProperties(locationId),
      )
      return MAPPING_FAILED
    }
  }

  suspend fun retryCreateLocationMapping(retryMessage: InternalMessage<LocationMappingDto>) {
    locationsMappingService.createMapping(
      retryMessage.body,
      object : ParameterizedTypeReference<DuplicateErrorResponse<LocationMappingDto>>() {},
    ).also {
      telemetryClient.trackEvent(
        "location-mapping-created-synchronisation-success",
        retryMessage.telemetryAttributes,
      )
    }
  }
}

private fun LocationsOffenderEvent.toTelemetryProperties(
  dpsLocationId: String? = null,
  mappingFailed: Boolean? = null,
) = mapOf(
  "nomisLocationId" to this.internalLocationId.toString(),
) + (dpsLocationId?.let { mapOf("dpsLocationId" to it) } ?: emptyMap()) + (
  mappingFailed?.takeIf { it }
    ?.let { mapOf("mapping" to "initial-failure") } ?: emptyMap()
  )
