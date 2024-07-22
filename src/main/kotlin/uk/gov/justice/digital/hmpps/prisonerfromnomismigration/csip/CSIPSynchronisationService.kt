package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.MappingResponse.MAPPING_FAILED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
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
        "csip-synchronisation-created-skipped",
        event.toTelemetryProperties(),
      )
      return
    }

    val nomisCSIP = nomisApiService.getCSIP(event.csipReportId)
    mappingApiService.getCSIPReportByNomisId(
      nomisCSIPReportId = event.csipReportId,
    )?.let {
      telemetryClient.trackEvent(
        "csip-synchronisation-created-ignored",
        event.toTelemetryProperties(it.dpsCSIPId),
      )
    } ?: let {
      csipService.createCSIPReport(event.offenderIdDisplay, nomisCSIP.toDPSCreateRequest(), nomisCSIP.createdBy)
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

  suspend fun csipReportReferralUpdated(event: CSIPReportEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCSIPId" to event.csipReportId,
        "offenderNo" to event.offenderIdDisplay,
      )

    // THIS COULD NEVER HAPPEN as filtering on audit module name to get here
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "csip-synchronisation-updated-skipped",
        telemetry + ("reason" to "CSIP Report for main screen not mapped"),
      )
    } else {
      val nomisCSIPResponse = nomisApiService.getCSIP(event.csipReportId)

      val mapping = mappingApiService.getCSIPReportByNomisId(event.csipReportId)
      if (mapping == null) {
        // Should never happen
        telemetryClient.trackEvent("csip-synchronisation-updated-failed", telemetry)
        throw IllegalStateException("Received CSIP_REPORTS-UPDATED - main screen - for csip that has never been created")
      } else {
        csipService.updateCSIPReferral(
          csipReportId = mapping.dpsCSIPId,
          nomisCSIPResponse.toDPSUpdateReferralRequest(),
          updatedByUsername = nomisCSIPResponse.lastModifiedUser(),
        )

        telemetryClient.trackEvent(
          "csip-synchronisation-updated-success",
          telemetry + ("dpsCSIPId" to mapping.dpsCSIPId),
        )
      }
    }
  }

  suspend fun csipReportReferralContUpdated(event: CSIPReportEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCSIPId" to event.csipReportId,
        "offenderNo" to event.offenderIdDisplay,
      )

    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "csip-synchronisation-updated-skipped",
        telemetry + ("reason" to "CSIP Report for Referral cont not mapped"),
      )
    } else {
      val nomisCSIPResponse = nomisApiService.getCSIP(event.csipReportId)

      val mapping = mappingApiService.getCSIPReportByNomisId(event.csipReportId)
      if (mapping == null) {
        // Should never happen
        telemetryClient.trackEvent("csip-synchronisation-updated-failed", telemetry)
        throw IllegalStateException("Received CSIP_REPORTS-UPDATED - referral continued - for csip that has never been created")
      } else {
        csipService.updateCSIPReferral(
          csipReportId = mapping.dpsCSIPId,
          nomisCSIPResponse.toDPSUpdateReferralContRequest(),
          updatedByUsername = nomisCSIPResponse.lastModifiedUser(),
        )
        telemetryClient.trackEvent(
          "csip-synchronisation-updated-success",
          telemetry + ("dpsCSIPId" to mapping.dpsCSIPId),
        )
      }
    }
  }
  fun CSIPResponse.lastModifiedUser() = lastModifiedBy ?: createdBy

  suspend fun csipReportDeleted(event: CSIPReportEvent) {
    mappingApiService.getCSIPReportByNomisId(nomisCSIPReportId = event.csipReportId)
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
    mappingApiService.getCSIPReportByNomisId(nomisCSIPReportId = event.csipReportId)?.let {
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

  suspend fun csipReportInvestigationUpdated(event: CSIPReportEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCSIPId" to event.csipReportId,
        "offenderNo" to event.offenderIdDisplay,
      )

    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "csip-investigation-synchronisation-updated-skipped",
        telemetry + ("reason" to "CSIP Report for Investigation not mapped"),
      )
    } else {
      val nomisCSIPResponse = nomisApiService.getCSIP(event.csipReportId)

      val mapping = mappingApiService.getCSIPReportByNomisId(event.csipReportId)
      if (mapping == null) {
        // Should never happen
        telemetryClient.trackEvent("csip-synchronisation-updated-failed", telemetry)
        throw IllegalStateException("Received CSIP_REPORTS-UPDATED - investigation - for csip that has never been created")
      } else {
        csipService.updateCSIPInvestigation(
          csipReportId = mapping.dpsCSIPId,
          nomisCSIPResponse.investigation.toDPSUpdateInvestigationRequest(),
          updatedByUsername = nomisCSIPResponse.lastModifiedUser(),
        )
        telemetryClient.trackEvent(
          "csip-investigation-synchronisation-updated-success",
          telemetry + ("dpsCSIPId" to mapping.dpsCSIPId),
        )
      }
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
}

enum class MappingResponse {
  MAPPING_CREATED,
  MAPPING_FAILED,
}

private fun CSIPReportEvent.toTelemetryProperties(
  dpsCSIPReportId: String? = null,
  mappingFailed: Boolean? = null,
) = mapOf("nomisCSIPId" to "$csipReportId") +
  (dpsCSIPReportId?.let { mapOf("dpsCSIPId" to it) } ?: emptyMap()) +
  (if (mappingFailed == true) mapOf("mapping" to "initial-failure") else emptyMap())
