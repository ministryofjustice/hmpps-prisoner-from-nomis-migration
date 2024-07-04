package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.MappingResponse.MAPPING_FAILED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.ContributoryFactor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPFactorMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPFactorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_CSIP_FACTOR_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class CSIPSynchronisationService(
  private val nomisApiService: CSIPNomisApiService,
  private val mappingApiService: CSIPMappingService,
  private val csipService: CSIPService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun csipReportInserted(event: CSIPReportEvent) {
    // Avoid duplicate sync if originated from DPS
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "csip-synchronisation-skipped",
        event.toTelemetryProperties(),
      )
      return
    }

    val nomisCSIP = nomisApiService.getCSIP(event.csipReportId)
    mappingApiService.findCSIPReportByNomisId(
      nomisCSIPReportId = event.csipReportId,
    )?.let {
      telemetryClient.trackEvent(
        "csip-synchronisation-created-ignored",
        event.toTelemetryProperties(it.dpsCSIPId),
      )
    } ?: let {
      csipService.createCSIPReport(event.offenderIdDisplay, nomisCSIP.toDPSCreateCSIP(), nomisCSIP.createdBy)
        .also { dpsCsip ->
          tryToCreateCSIPReportMapping(event, dpsCsip.recordUuid.toString()).also { result ->
            telemetryClient.trackEvent(
              "csip-synchronisation-created-success",
              event.toTelemetryProperties(
                dpsCsip.recordUuid.toString(),
                result == MappingResponse.MAPPING_FAILED,
              ),
            )
          }
        }
    }
  }

  suspend fun csipReportDeleted(event: CSIPReportEvent) {
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "csip-synchronisation-deleted-skipped",
        event.toTelemetryProperties(),
      )
      return
    }

    mappingApiService.findCSIPReportByNomisId(nomisCSIPReportId = event.csipReportId)
      ?.let {
        log.debug("Found csip mapping: {}", it)
        csipService.deleteCSIP(it.dpsCSIPId)
        tryToDeleteCSIPReportMapping(it.dpsCSIPId)
        telemetryClient.trackEvent(
          "csip-synchronisation-deleted-success",
          event.toTelemetryProperties(dpsCSIPReportId = it.dpsCSIPId),
        )
      } ?: let {
      telemetryClient.trackEvent(
        "csip-synchronisation-deleted-ignored",
        event.toTelemetryProperties(),
      )
    }
  }

  suspend fun csipSaferCustodyScreeningInserted(event: CSIPReportEvent) {
    val nomisCSIP = nomisApiService.getCSIP(event.csipReportId)
    mappingApiService.findCSIPReportByNomisId(nomisCSIPReportId = event.csipReportId)?.let {
      csipService.createCSIPSaferCustodyScreening(
        it.dpsCSIPId,
        nomisCSIP.saferCustodyScreening.toDPSCreateCSIPSCS(),
        nomisCSIP.saferCustodyScreening.recordedBy!!,
      )
      telemetryClient.trackEvent(
        "csip-scs-synchronisation-created-success",
        event.toTelemetryProperties(it.dpsCSIPId),
      )
    } ?: let {
      // The CSIP Report Mapping  should exist already - if not, then the ordering of events may be incorrect
      telemetryClient.trackEvent(
        "csip-scs-synchronisation-created-failed",
        event.toTelemetryProperties() + ("reason" to "CSIP Report for CSIP SCS not mapped"),
      )
      throw IllegalStateException("Received CSIP_REPORTS_UPDATED for Safer Custody Screening that has never been created/mapped")
    }
  }

  suspend fun csipFactorInserted(event: CSIPFactorEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCsipReportId" to event.csipReportId,
        "offenderNo" to event.offenderIdDisplay,
        "nomisCsipFactorId" to event.csipFactorId,
      )

    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("csip-factor-synchronisation-skipped", telemetry)
      return
    }

    val nomisFactorResponse = nomisApiService.getCSIPFactor(event.csipFactorId)
    // Get the report mapping
    mappingApiService.findCSIPReportByNomisId(nomisCSIPReportId = event.csipReportId)
      ?.let { reportMapping ->
        mappingApiService.findCSIPFactorByNomisId(nomisCSIPFactorId = event.csipFactorId)
          ?.let {
            // Should never happen - factor should not exist in the mapping table
            telemetryClient.trackEvent(
              "csip-factor-synchronisation-created-ignored",
              telemetry + ("dpsCsipFactorId" to it.dpsCSIPFactorId) + ("reason" to "CSIP Factor already mapped"),
            )
          }
          ?: run {
            // HAPPY PATH
            csipService.createCSIPFactor(
              reportMapping.dpsCSIPId,
              nomisFactorResponse.toDPSFactorRequest(),
              nomisFactorResponse.createdBy,
            ).also {
              telemetry.put("dpsCsipFactorId", it.factorUuid.toString())

              tryToCreateCSIPFactorMapping(nomisFactorResponse, it, telemetry)
                .also { mappingCreateResult ->
                  if (mappingCreateResult == MAPPING_FAILED) telemetry.put("mapping", "initial-failure")
                  telemetryClient.trackEvent(
                    "csip-factor-synchronisation-created-success",
                    telemetry,
                  )
                }
            }
          }
      }
      ?: run {
        // Should never happen - report should exist in the mapping table
        telemetryClient.trackEvent(
          "csip-factor-synchronisation-created-failed",
          telemetry + ("reason" to "CSIP Report for CSIP factor not mapped"),
        )
        throw IllegalStateException("Received CSIP_FACTORS_INSERTED for csip Report that has never been created/mapped")
      }
  }

  private suspend fun tryToCreateCSIPReportMapping(
    event: CSIPReportEvent,
    dpsCSIPId: String,
  ): MappingResponse {
    val mapping = CSIPMappingDto(
      nomisCSIPId = event.csipReportId,
      dpsCSIPId = dpsCSIPId,
      mappingType = CSIPMappingDto.MappingType.NOMIS_CREATED,
    )
    try {
      mappingApiService.createMapping(
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
        "Failed to create mapping for csip report dpsCSIPId id $dpsCSIPId, nomisCSIPId ${event.csipReportId}",
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

  suspend fun retryCreateCSIPReportMapping(retryMessage: InternalMessage<CSIPMappingDto>) {
    mappingApiService.createMapping(
      retryMessage.body,
      object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPMappingDto>>() {},
    ).also {
      telemetryClient.trackEvent(
        "csip-synchronisation-mapping-created-success",
        retryMessage.telemetryAttributes,
      )
    }
  }

  private suspend fun tryToDeleteCSIPReportMapping(dpsCSIPId: String) = runCatching {
    mappingApiService.deleteCSIPReportMappingByDPSId(dpsCSIPId)
  }.onFailure { e ->
    telemetryClient.trackEvent("csip-mapping-deleted-failed", mapOf("dpsCSIPId" to dpsCSIPId))
    log.warn("Unable to delete mapping for csip report $dpsCSIPId. Please delete manually", e)
  }

  private suspend fun tryToCreateCSIPFactorMapping(
    nomisCSIPFactor: CSIPFactorResponse,
    dpsCSIPFactor: ContributoryFactor,
    telemetry: Map<String, Any>,
  ): MappingResponse {
    val mapping = CSIPFactorMappingDto(
      nomisCSIPFactorId = nomisCSIPFactor.id,
      dpsCSIPFactorId = dpsCSIPFactor.factorUuid.toString(),
      mappingType = CSIPFactorMappingDto.MappingType.NOMIS_CREATED,
    )
    try {
      mappingApiService.createCSIPFactorMapping(
        mapping,
      ).also {
        if (it.isError) {
          val duplicateErrorDetails = (it.errorResponse!!).moreInfo
          telemetryClient.trackEvent(
            "csip-factor-synchronisation-from-nomis-duplicate",
            mapOf(
              "existingNomisCSIPFactorId" to duplicateErrorDetails.existing.nomisCSIPFactorId,
              "duplicateNomisCSIPFactorId" to duplicateErrorDetails.duplicate.nomisCSIPFactorId,
              "existingDPSCSIPFactorId" to duplicateErrorDetails.existing.dpsCSIPFactorId,
              "duplicateDPSCSIPFactorId" to duplicateErrorDetails.duplicate.dpsCSIPFactorId,
            ),
          )
        }
      }
      return MappingResponse.MAPPING_CREATED
    } catch (e: Exception) {
      log.error("Failed to create mapping for sentence ids $mapping", e)
      queueService.sendMessage(
        messageType = RETRY_CSIP_FACTOR_SYNCHRONISATION_MAPPING,
        synchronisationType = SynchronisationType.CSIP,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
      return MappingResponse.MAPPING_FAILED
    }
  }
}

private enum class MappingResponse {
  MAPPING_CREATED,
  MAPPING_FAILED,
}

private fun CSIPReportEvent.toTelemetryProperties(
  dpsCSIPReportId: String? = null,
  mappingFailed: Boolean? = null,
) = mapOf(
  "nomisCSIPId" to "$csipReportId",
) + (dpsCSIPReportId?.let { mapOf("dpsCSIPId" to it) } ?: emptyMap()) + (
  mappingFailed?.takeIf { it }
    ?.let { mapOf("mapping" to "initial-failure") } ?: emptyMap()
  )

private fun CSIPFactorEvent.toTelemetryPropertie9s(
  dpsCSIPReportId: String? = null,
  dpsCSIPFactorId: String? = null,
  mappingFailed: Boolean? = null,
) = mapOf(
  "nomisCSIPId" to "$csipReportId",
  "nomisCSIPFactorId" to "$csipFactorId",
) +
  (dpsCSIPReportId?.let { mapOf("dpsCSIPId" to it) } ?: emptyMap()) +
  (dpsCSIPFactorId?.let { mapOf("dpsCSIPFactorId" to it) } ?: emptyMap()) +
  (
    mappingFailed?.takeIf { it }
      ?.let { mapOf("mapping" to "initial-failure") } ?: emptyMap()
    )
