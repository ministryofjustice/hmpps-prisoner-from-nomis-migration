package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.MappingResponse.MAPPING_FAILED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPFullMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class CSIPSynchronisationService(
  private val nomisApiService: CSIPNomisApiService,
  private val mappingApiService: CSIPMappingService,
  private val csipDpsService: CSIPDpsApiService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun csipReportInserted(event: CSIPReportEvent) {
    val telemetry = mapOf(
      "nomisCSIPId" to event.csipReportId,
      "offenderNo" to event.offenderIdDisplay,
    )

    // Avoid duplicate sync if originated from DPS
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("csip-synchronisation-created-skipped", telemetry)
      return
    }

    val nomisCSIP = nomisApiService.getCSIP(event.csipReportId)
    mappingApiService.getCSIPReportByNomisId(nomisCSIPReportId = event.csipReportId)
      ?.let {
        telemetryClient.trackEvent(
          "csip-synchronisation-created-ignored",
          telemetry + ("dpsCSIPId" to it.dpsCSIPReportId),
        )
      }
      ?: let {
        // CSIP Report mapping doesn't exist
        val syncCsipRequest = nomisCSIP.toDPSSyncRequest(actioned = nomisCSIP.toActionDetails())

        csipDpsService.syncCSIP(syncCsipRequest)
          .also { syncResponse ->
            val dpsCSIPReportId = syncResponse.filterReport().uuid.toString()
            tryToCreateCSIPMapping(
              event.csipReportId,
              CSIPFullMappingDto(
                nomisCSIPReportId = event.csipReportId,
                dpsCSIPReportId = dpsCSIPReportId,
                mappingType = CSIPFullMappingDto.MappingType.NOMIS_CREATED,
                attendeeMappings = syncResponse.filterAttendees(dpsCSIPReportId = dpsCSIPReportId),
                factorMappings = syncResponse.filterFactors(dpsCSIPReportId = dpsCSIPReportId),
                interviewMappings = syncResponse.filterInterviews(dpsCSIPReportId = dpsCSIPReportId),
                planMappings = syncResponse.filterPlans(dpsCSIPReportId = dpsCSIPReportId),
                reviewMappings = syncResponse.filterReviews(dpsCSIPReportId = dpsCSIPReportId),

              ),
              telemetry + ("dpsCSIPId" to dpsCSIPReportId),
            )
              .also { result ->
                val mappingSuccessTelemetry =
                  (if (result == MappingResponse.MAPPING_CREATED) mapOf() else mapOf("mapping" to "initial-failure"))
                telemetryClient.trackEvent(
                  "csip-synchronisation-created-success",
                  telemetry + ("dpsCSIPId" to dpsCSIPReportId) + mappingSuccessTelemetry,
                )
              }
          }
      }
  }

  suspend fun csipReportUpdated(event: CSIPReportEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCSIPId" to event.csipReportId,
        "offenderNo" to event.offenderIdDisplay,
      )

    // Avoid duplicate sync if originated from DPS
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "csip-synchronisation-updated-skipped",
        telemetry,
      )
      return
    }
    val nomisCSIPResponse = nomisApiService.getCSIP(event.csipReportId)
    val actionDetails = nomisCSIPResponse.toActionDetails()
    synchronise(nomisCSIPResponse, telemetry, actionDetails)
  }

  suspend fun csipFactorUpserted(event: CSIPFactorEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCSIPId" to event.csipReportId,
        "nomisCSIPFactorId" to event.csipFactorId,
        "offenderNo" to event.offenderIdDisplay,
      )

    // Avoid duplicate sync if originated from DPS
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent(
        "csip-synchronisation-updated-skipped",
        telemetry,
      )
      return
    }

    val nomisCSIPResponse = nomisApiService.getCSIP(event.csipReportId)
    // Determine Action details
    val actionDetails = nomisCSIPResponse.reportDetails.factors.find { it.id == event.csipFactorId }?.toActionDetails()

    // Should never happen
    if (actionDetails == null) {
      telemetryClient.trackEvent("csip-synchronisation-updated-failed", telemetry)
      throw IllegalStateException("Received CSIP_FACTORS_INSERTED for a csip factor that does not exist")
    }
    synchronise(nomisCSIPResponse, telemetry, actionDetails)
  }

  suspend fun synchronise(nomisCSIPResponse: CSIPResponse, telemetry: Map<String, Any>, actionDetails: ActionDetails) {
    val reportMapping = mappingApiService.getCSIPReportByNomisId(nomisCSIPResponse.id)
    if (reportMapping == null) {
      // Should never happen
      telemetryClient.trackEvent("csip-synchronisation-updated-failed", telemetry)
      throw IllegalStateException("Received CSIP UPDATED for a csip that has never been created")
    } else {
      val dpsCSIPReportId = reportMapping.dpsCSIPReportId

      // For an update we need to populate the Sync Request with any existing dps mappings
      val fullMappingDto = mappingApiService.getFullMappingByDPSReportId(dpsCSIPReportId)

      val syncCsipRequest = nomisCSIPResponse.toDPSSyncRequest(
        dpsReportId = dpsCSIPReportId,
        actioned = actionDetails,
        fullMappingDto = fullMappingDto,
      )

      csipDpsService.syncCSIP(syncCsipRequest)
        .also { syncResponse ->
          // Only create mappings if we have new child ids to map
          if (syncResponse.mappings.isNotEmpty()) {
            tryToCreateChildMappings(
              nomisCSIPResponse.id,
              CSIPFullMappingDto(
                nomisCSIPReportId = nomisCSIPResponse.id,
                dpsCSIPReportId = dpsCSIPReportId,
                mappingType = CSIPFullMappingDto.MappingType.NOMIS_CREATED,
                attendeeMappings = syncResponse.filterAttendees(dpsCSIPReportId = dpsCSIPReportId),
                factorMappings = syncResponse.filterFactors(dpsCSIPReportId = dpsCSIPReportId),
                interviewMappings = syncResponse.filterInterviews(dpsCSIPReportId = dpsCSIPReportId),
                planMappings = syncResponse.filterPlans(dpsCSIPReportId = dpsCSIPReportId),
                reviewMappings = syncResponse.filterReviews(dpsCSIPReportId = dpsCSIPReportId),
              ),
              telemetry + ("dpsCSIPId" to dpsCSIPReportId),
            ).also { result ->
              val mappingSuccessTelemetry =
                (if (result == MappingResponse.MAPPING_CREATED) mapOf() else mapOf("mapping" to "initial-failure"))
              telemetryClient.trackEvent(
                "csip-synchronisation-updated-success",
                telemetry + ("dpsCSIPId" to dpsCSIPReportId) + mappingSuccessTelemetry,
              )
            }
          } else {
            telemetryClient.trackEvent(
              "csip-synchronisation-updated-success",
              telemetry + ("dpsCSIPId" to dpsCSIPReportId),
            )
          }
        }
    }
  }

  /*
  suspend fun csipFactorUpserted(event: CSIPFactorEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCSIPReportId" to event.csipReportId,
        "offenderNo" to event.offenderIdDisplay,
        "nomisCSIPFactorId" to event.csipFactorId,
      )

    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("csip-factor-synchronisation-skipped", telemetry)
      return
    }

    val nomisCSIPResponse = nomisApiService.getCSIP(event.csipReportId)

    val factorResponse = nomisCSIPResponse.reportDetails.factors.find { it.id == event.csipReportId }
    val actionedDetailsForCSIPFactor = factorResponse!!.toActionDetails()
  }
*/

  suspend fun csipReportDeleted(event: CSIPReportEvent) {
    val telemetry =
      mapOf(
        "nomisCSIPId" to event.csipReportId,
        "offenderNo" to event.offenderIdDisplay,
      )

    mappingApiService.getCSIPReportByNomisId(nomisCSIPReportId = event.csipReportId)
      ?.let {
        log.debug("Found csip mapping: {}", it)
        csipDpsService.deleteCSIP(it.dpsCSIPReportId)
        // This will remove all child mappings
        tryToDeleteCSIPReportMapping(it.dpsCSIPReportId)
        telemetryClient.trackEvent(
          "csip-synchronisation-deleted-success",
          telemetry + ("dpsCSIPId" to it.dpsCSIPReportId),
        )
      } ?: let {
      telemetryClient.trackEvent(
        "csip-synchronisation-deleted-ignored",
        telemetry,
      )
    }
  }

  private suspend fun tryToCreateCSIPMapping(
    nomisCsipReportId: Long,
    fullMappingDto: CSIPFullMappingDto,
    telemetry: Map<String, Any>,
  ): MappingResponse {
    try {
      mappingApiService.createMapping(
        fullMappingDto,
        object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPFullMappingDto>>() {},
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
        "Failed to create mapping for csip report dpsCSIPId id ${fullMappingDto.dpsCSIPReportId}, nomisCSIPId $nomisCsipReportId",
        e,
      )
      queueService.sendMessage(
        messageType = SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = SynchronisationType.CSIP,
        message = fullMappingDto,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
      return MAPPING_FAILED
    }
  }

  private suspend fun tryToCreateChildMappings(
    nomisCsipReportId: Long,
    fullMappingDto: CSIPFullMappingDto,
    telemetry: Map<String, Any>,
  ): MappingResponse {
    try {
      mappingApiService.createChildMappings(
        fullMappingDto,
        object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPFullMappingDto>>() {},
      )
        .also {
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
      return MappingResponse.MAPPING_UPDATED
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for csip report dpsCSIPId id ${fullMappingDto.dpsCSIPReportId}, nomisCSIPId $nomisCsipReportId",
        e,
      )
      queueService.sendMessage(
        messageType = SynchronisationMessageType.RETRY_SYNCHRONISATION_CHILD_MAPPING.name,
        synchronisationType = SynchronisationType.CSIP,
        message = fullMappingDto,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
      return MAPPING_FAILED
    }
  }

  suspend fun retryCreateCSIPReportMapping(retryMessage: InternalMessage<CSIPFullMappingDto>) {
    mappingApiService.createMapping(
      retryMessage.body,
      object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPFullMappingDto>>() {},
    ).also {
      telemetryClient.trackEvent(
        "csip-mapping-created-synchronisation-success",
        retryMessage.telemetryAttributes,
      )
    }
  }

  suspend fun retryUpdateCSIPReportMapping(retryMessage: InternalMessage<CSIPFullMappingDto>) {
    mappingApiService.createChildMappings(
      retryMessage.body,
      object : ParameterizedTypeReference<DuplicateErrorResponse<CSIPFullMappingDto>>() {},
    ).also {
      telemetryClient.trackEvent(
        "csip-mapping-updated-synchronisation-success",
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
  MAPPING_UPDATED,
  MAPPING_FAILED,
}
