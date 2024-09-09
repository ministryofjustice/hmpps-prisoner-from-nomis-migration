package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.MappingResponse.MAPPING_FAILED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.ResponseMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPReportMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class CSIPSynchronisationService(
  private val nomisApiService: CSIPNomisApiService,
  private val mappingApiService: CSIPMappingService,
  private val csipService: CSIPDpsApiService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun csipReportSynchronise(event: CSIPReportEvent) {
    // Avoid duplicate sync if originated from DPS
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "csip-synchronisation-created-skipped",
        event.toTelemetryProperties(),
      )
      return
    }

    val telemetry = mutableMapOf(
      "nomisCSIPId" to event.csipReportId,
      "offenderNo" to event.offenderIdDisplay,
    )

    val nomisCSIP = nomisApiService.getCSIP(event.csipReportId)
    mappingApiService.getCSIPReportByNomisId(nomisCSIPReportId = event.csipReportId)
      ?.let {
        // TODO CSIP Report mapping exists so we just want to update
        //  csipService.syncCSIP(nomisCSIP.toDPSSyncRequest(it.dpsCSIPId), nomisCSIP.createdBy)
        telemetryClient.trackEvent(
          "csip-synchronisation-created-ignored",
          event.toTelemetryProperties(it.dpsCSIPReportId),
        )
      }
      ?: let {
        // CSIP Report mapping doesn't exist
        csipService.syncCSIP(nomisCSIP.toDPSSyncRequest(), nomisCSIP.createdBy)
          .also { syncResponse ->
            // At this point we need to determine all mappings and call the appropriate mapping endpoints
            // For now, just map top level report
            val csipReport = syncResponse.mappings.first { it.component == ResponseMapping.Component.RECORD }
            val dpsCSIPReportId = csipReport.uuid.toString()

            tryToCreateCSIPReportMapping(event, dpsCSIPReportId).also { result ->
              val mappingSuccessTelemetry =
                (if (result == MappingResponse.MAPPING_CREATED) mapOf() else mapOf("mapping" to "initial-failure"))
              telemetryClient.trackEvent(
                "csip-synchronisation-created-success",
                telemetry + ("dpsCSIPId" to dpsCSIPReportId) + mappingSuccessTelemetry,
              )
            }
            // TODO **** CHECK IF CHILD TABLES NEED MAPPING  ****
          }
      }
  }

  suspend fun csipReportReferralUpdated(event: CSIPReportEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCSIPId" to event.csipReportId,
        "offenderNo" to event.offenderIdDisplay,
      )

    val nomisCSIPResponse = nomisApiService.getCSIP(event.csipReportId)

    val mapping = mappingApiService.getCSIPReportByNomisId(event.csipReportId)
    if (mapping == null) {
      // Should never happen
      telemetryClient.trackEvent("csip-synchronisation-updated-failed", telemetry)
      throw IllegalStateException("Received CSIP_REPORTS-UPDATED - main screen - for csip that has never been created")
    } else {
      csipService.updateCSIPReferral(
        csipReportId = mapping.dpsCSIPReportId,
        nomisCSIPResponse.toDPSUpdateCsipRecordRequest(),
        updatedByUsername = nomisCSIPResponse.lastModifiedUser(),
      )

      telemetryClient.trackEvent(
        "csip-synchronisation-updated-success",
        telemetry + ("dpsCSIPId" to mapping.dpsCSIPReportId),
      )
    }
  }

  suspend fun csipReportReferralContUpdated(event: CSIPReportEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCSIPId" to event.csipReportId,
        "offenderNo" to event.offenderIdDisplay,
      )

    val nomisCSIPResponse = nomisApiService.getCSIP(event.csipReportId)

    val mapping = mappingApiService.getCSIPReportByNomisId(event.csipReportId)
    if (mapping == null) {
      // Should never happen
      telemetryClient.trackEvent("csip-synchronisation-updated-failed", telemetry)
      throw IllegalStateException("Received CSIP_REPORTS-UPDATED - referral continued - for csip that has never been created")
    } else {
      csipService.updateCSIPReferral(
        csipReportId = mapping.dpsCSIPReportId,
        nomisCSIPResponse.toDPSUpdateReferralContRequest(),
        updatedByUsername = nomisCSIPResponse.lastModifiedUser(),
      )
      telemetryClient.trackEvent(
        "csip-synchronisation-updated-success",
        telemetry + ("dpsCSIPId" to mapping.dpsCSIPReportId),
      )
    }
  }

  fun CSIPResponse.lastModifiedUser() = lastModifiedBy ?: createdBy

  suspend fun csipReportDeleted(event: CSIPReportEvent) {
    mappingApiService.getCSIPReportByNomisId(nomisCSIPReportId = event.csipReportId)
      ?.let {
        log.debug("Found csip mapping: {}", it)
        csipService.deleteCSIP(it.dpsCSIPReportId)
        // This will remove all child mappings
        tryToDeleteCSIPReportMapping(it.dpsCSIPReportId)
        telemetryClient.trackEvent(
          "csip-synchronisation-deleted-success",
          event.toTelemetryProperties(dpsCSIPReportId = it.dpsCSIPReportId),
        )
      } ?: let {
      telemetryClient.trackEvent(
        "csip-synchronisation-deleted-ignored",
        event.toTelemetryProperties(),
      )
    }
  }

  suspend fun csipPlanDeleted(event: CSIPPlanEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCSIPReportId" to event.csipReportId,
        "offenderNo" to event.offenderIdDisplay,
        "nomisCSIPPlanId" to event.csipPlanId,
      )
    mappingApiService.getCSIPPlanByNomisId(nomisCSIPPlanId = event.csipPlanId)
      ?.let { mapping ->
        log.debug("Found csip plan mapping: {}", mapping)
        csipService.deleteCSIPPlan(mapping.dpsCSIPPlanId)
        tryToDeleteCSIPPlanMapping(mapping.dpsCSIPPlanId)
        telemetryClient.trackEvent(
          "csip-plan-synchronisation-deleted-success",
          telemetry + ("dpsCSIPPlanId" to mapping.dpsCSIPPlanId),
        )
      } ?: let {
      telemetryClient.trackEvent("csip-plan-synchronisation-deleted-ignored", telemetry)
    }
  }

  suspend fun csipInterviewDeleted(event: CSIPInterviewEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCSIPReportId" to event.csipReportId,
        "offenderNo" to event.offenderIdDisplay,
        "nomisCSIPInterviewId" to event.csipInterviewId,
      )
    mappingApiService.getCSIPInterviewByNomisId(nomisCSIPInterviewId = event.csipInterviewId)
      ?.let { mapping ->
        log.debug("Found csip interview mapping: {}", mapping)
        csipService.deleteCSIPInterview(mapping.dpsCSIPInterviewId)
        tryToDeleteCSIPInterviewMapping(mapping.dpsCSIPInterviewId)
        telemetryClient.trackEvent(
          "csip-interview-synchronisation-deleted-success",
          telemetry + ("dpsCSIPInterviewId" to mapping.dpsCSIPInterviewId),
        )
      } ?: let {
      telemetryClient.trackEvent("csip-interview-synchronisation-deleted-ignored", telemetry)
    }
  }

  suspend fun csipAttendeeDeleted(event: CSIPAttendeeEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCSIPReportId" to event.csipReportId,
        "offenderNo" to event.offenderIdDisplay,
        "nomisCSIPAttendeeId" to event.csipAttendeeId,
      )
    mappingApiService.getCSIPAttendeeByNomisId(nomisCSIPAttendeeId = event.csipAttendeeId)
      ?.let { mapping ->
        log.debug("Found csip attendee mapping: {}", mapping)
        csipService.deleteCSIPAttendee(mapping.dpsCSIPAttendeeId)
        tryToDeleteCSIPAttendeeMapping(mapping.dpsCSIPAttendeeId)
        telemetryClient.trackEvent(
          "csip-attendee-synchronisation-deleted-success",
          telemetry + ("dpsCSIPAttendeeId" to mapping.dpsCSIPAttendeeId),
        )
      } ?: let {
      telemetryClient.trackEvent("csip-attendee-synchronisation-deleted-ignored", telemetry)
    }
  }

  suspend fun csipSaferCustodyScreeningInserted(event: CSIPReportEvent) {
    val nomisCSIP = nomisApiService.getCSIP(event.csipReportId)
    mappingApiService.getCSIPReportByNomisId(nomisCSIPReportId = event.csipReportId)?.let {
      csipService.createCSIPSaferCustodyScreening(
        it.dpsCSIPReportId,
        nomisCSIP.saferCustodyScreening.toDPSCreateCSIPSCS(),
        nomisCSIP.saferCustodyScreening.recordedBy!!,
      )
      telemetryClient.trackEvent(
        "csip-scs-synchronisation-created-success",
        event.toTelemetryProperties(it.dpsCSIPReportId),
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

  suspend fun csipInvestigationUpdated(event: CSIPReportEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCSIPId" to event.csipReportId,
        "offenderNo" to event.offenderIdDisplay,
      )

    val nomisCSIPResponse = nomisApiService.getCSIP(event.csipReportId)

    val mapping = mappingApiService.getCSIPReportByNomisId(event.csipReportId)
    if (mapping == null) {
      // Should never happen
      telemetryClient.trackEvent("csip-synchronisation-updated-failed", telemetry)
      throw IllegalStateException("Received CSIP_REPORTS-UPDATED - investigation - for csip that has never been created")
    } else {
      csipService.updateCSIPInvestigation(
        csipReportId = mapping.dpsCSIPReportId,
        nomisCSIPResponse.investigation.toDPSUpdateInvestigationRequest(),
        updatedByUsername = nomisCSIPResponse.lastModifiedUser(),
      )
      telemetryClient.trackEvent(
        "csip-investigation-synchronisation-updated-success",
        telemetry + ("dpsCSIPId" to mapping.dpsCSIPReportId),
      )
    }
  }

  suspend fun csipDecisionUpdated(event: CSIPReportEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCSIPId" to event.csipReportId,
        "offenderNo" to event.offenderIdDisplay,
      )

    val nomisCSIPResponse = nomisApiService.getCSIP(event.csipReportId)
    val mapping = mappingApiService.getCSIPReportByNomisId(event.csipReportId)
    if (mapping == null) {
      // Should never happen
      telemetryClient.trackEvent("csip-synchronisation-updated-failed", telemetry)
      throw IllegalStateException("Received CSIP_REPORTS-UPDATED - decision - for csip that has never been created")
    } else {
      csipService.updateCSIPDecision(
        csipReportId = mapping.dpsCSIPReportId,
        nomisCSIPResponse.decision.toDPSUpsertDecisionsAndActionsRequest(),
        updatedByUsername = nomisCSIPResponse.lastModifiedUser(),
      )
      telemetryClient.trackEvent(
        "csip-decision-synchronisation-updated-success",
        telemetry + ("dpsCSIPId" to mapping.dpsCSIPReportId),
      )
    }
  }

  suspend fun csipPlanUpdated(event: CSIPReportEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCSIPId" to event.csipReportId,
        "offenderNo" to event.offenderIdDisplay,
      )

    val nomisCSIPResponse = nomisApiService.getCSIP(event.csipReportId)
    val mapping = mappingApiService.getCSIPReportByNomisId(event.csipReportId)
    if (mapping == null) {
      // Should never happen
      telemetryClient.trackEvent("csip-synchronisation-updated-failed", telemetry)
      throw IllegalStateException("Received CSIP_REPORTS-UPDATED - plan - for csip that has never been created")
    } else {
      csipService.updateCSIPPlan(
        csipReportId = mapping.dpsCSIPReportId,
        nomisCSIPResponse.toDPSUpsertPlanRequest(),
        updatedByUsername = nomisCSIPResponse.lastModifiedUser(),
      )
      telemetryClient.trackEvent(
        "csip-plan-synchronisation-updated-success",
        telemetry + ("dpsCSIPId" to mapping.dpsCSIPReportId),
      )
    }
  }

  private suspend fun tryToCreateCSIPReportMapping(
    event: CSIPReportEvent,
    dpsCSIPId: String,
  ): MappingResponse {
    val mapping = CSIPReportMappingDto(
      nomisCSIPReportId = event.csipReportId,
      dpsCSIPReportId = dpsCSIPId,
      mappingType = CSIPReportMappingDto.MappingType.NOMIS_CREATED,
    )
    try {
      mappingApiService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPReportMappingDto>>() {},
      ).also {
        if (it.isError) {
          val duplicateErrorDetails = (it.errorResponse!!).moreInfo
          telemetryClient.trackEvent(
            "csip-synchronisation-from-nomis-duplicate",
            mapOf<String, String>(
              "existingNomisCSIPId" to duplicateErrorDetails.existing.nomisCSIPReportId.toString(),
              "duplicateNomisCSIPId" to duplicateErrorDetails.duplicate.nomisCSIPReportId.toString(),
              "existingDPSCSIPId" to duplicateErrorDetails.existing.dpsCSIPReportId,
              "duplicateDPSCSIPId" to duplicateErrorDetails.duplicate.dpsCSIPReportId,
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

  suspend fun retryCreateCSIPReportMapping(retryMessage: InternalMessage<CSIPReportMappingDto>) {
    mappingApiService.createMapping(
      retryMessage.body,
      object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPReportMappingDto>>() {},
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

  private suspend fun tryToDeleteCSIPPlanMapping(dpsCSIPPlanId: String) = runCatching {
    mappingApiService.deleteCSIPPlanMappingByDPSId(dpsCSIPPlanId)
  }.onFailure { e ->
    telemetryClient.trackEvent("csip-plan-mapping-deleted-failed", mapOf("dpsCSIPPlanId" to dpsCSIPPlanId))
    log.warn("Unable to delete mapping for csip plan $dpsCSIPPlanId. Please delete manually", e)
  }

  private suspend fun tryToDeleteCSIPInterviewMapping(dpsCSIPInterviewId: String) = runCatching {
    mappingApiService.deleteCSIPInterviewMappingByDPSId(dpsCSIPInterviewId)
  }.onFailure { e ->
    telemetryClient.trackEvent("csip-interview-mapping-deleted-failed", mapOf("dpsCSIPInterviewId" to dpsCSIPInterviewId))
    log.warn("Unable to delete mapping for csip interview $dpsCSIPInterviewId. Please delete manually", e)
  }

  private suspend fun tryToDeleteCSIPAttendeeMapping(dpsCSIPAttendeeId: String) = runCatching {
    mappingApiService.deleteCSIPAttendeeMappingByDPSId(dpsCSIPAttendeeId)
  }.onFailure { e ->
    telemetryClient.trackEvent("csip-attendee-mapping-deleted-failed", mapOf("dpsCSIPAttendeeId" to dpsCSIPAttendeeId))
    log.warn("Unable to delete mapping for csip attendee $dpsCSIPAttendeeId. Please delete manually", e)
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
