package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPSynchronisationService.MappingResponse.MAPPING_FAILED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class CSIPSynchronisationService(
  private val nomisApiService: CSIPNomisApiService,
  private val csipMappingApiService: CSIPMappingService,
  private val csipService: CSIPService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun synchroniseCSIPInsert(event: CSIPOffenderEvent) {
    // Should never happen
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "csip-synchronisation-skipped",
        event.toTelemetryProperties(),
      )
      return
    }

    val nomisCSIP = nomisApiService.getCSIP(event.nomisCSIPId)
    csipMappingApiService.findNomisCSIPMapping(
      nomisCSIPId = event.nomisCSIPId,
    )?.let {
      telemetryClient.trackEvent(
        "csip-synchronisation-created-ignored",
        event.toTelemetryProperties(it.dpsCSIPId),
      )
    } ?: let {
      csipService.insertCSIP(
        CSIPSyncRequest(
          nomisCSIPId = nomisCSIP.id,
          concernDescription = nomisCSIP.reportDetails.concern,
        ),
      ).also { csip ->
        tryToCreateCSIPMapping(event, csip.dpsCSIPId).also { result ->
          telemetryClient.trackEvent(
            "csip-synchronisation-created-success",
            event.toTelemetryProperties(
              csip.dpsCSIPId,
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

  suspend fun tryToCreateCSIPMapping(
    event: CSIPOffenderEvent,
    dpsCSIPId: String,
  ): MappingResponse {
    val mapping = CSIPMappingDto(
      nomisCSIPId = event.nomisCSIPId,
      dpsCSIPId = dpsCSIPId,
      mappingType = CSIPMappingDto.MappingType.NOMIS_CREATED,
    )
    try {
      csipMappingApiService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPMappingDto>>() {},
      ).also {
        if (it.isError) {
          val duplicateErrorDetails = (it.errorResponse!!).moreInfo
          telemetryClient.trackEvent(
            "csip-synchronisation-from-nomis-duplicate",
            mapOf<String, String>(
              "existingNomisCSIPId" to duplicateErrorDetails.existing.nomisCSIPId.toString(),
              "duplicateNomisCSIPId" to duplicateErrorDetails.duplicate.nomisCSIPId.toString(),
              "existingDPSCSIPId" to duplicateErrorDetails.existing.dpsCSIPId,
              "duplicateDPSCSIPId" to duplicateErrorDetails.duplicate.dpsCSIPId,
            ),
            null,
          )
        }
      }
      return MappingResponse.MAPPING_CREATED
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for dpsCSIPId id $dpsCSIPId, nomisCSIPId ${event.nomisCSIPId}",
        e,
      )
      queueService.sendMessage(
        messageType = SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = SynchronisationType.CSIP,
        message = mapping,
        telemetryAttributes = event.toTelemetryProperties(dpsCSIPId),
      )
      return MAPPING_FAILED
    }
  }

  suspend fun retryCreateCSIPMapping(retryMessage: InternalMessage<CSIPMappingDto>) {
    csipMappingApiService.createMapping(
      retryMessage.body,
      object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPMappingDto>>() {},
    ).also {
      telemetryClient.trackEvent(
        "csip-synchronisation-mapping-created-success",
        retryMessage.telemetryAttributes,
      )
    }
  }
}
private fun CSIPOffenderEvent.isSourcedFromDPS() = auditModuleName == "DPS_SYNCHRONISATION"

private fun CSIPOffenderEvent.toTelemetryProperties(
  dpsCSIPId: String? = null,
  mappingFailed: Boolean? = null,
) = mapOf(
  "nomisCSIPId" to "$nomisCSIPId",
) + (dpsCSIPId?.let { mapOf("dpsCSIPId" to it) } ?: emptyMap()) + (
  mappingFailed?.takeIf { it }
    ?.let { mapOf("mapping" to "initial-failure") } ?: emptyMap()
  )
