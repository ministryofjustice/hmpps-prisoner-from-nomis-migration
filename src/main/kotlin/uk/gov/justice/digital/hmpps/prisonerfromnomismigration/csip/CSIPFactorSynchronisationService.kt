package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.ContributoryFactor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPFactorMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPFactorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_CSIP_FACTOR_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class CSIPFactorSynchronisationService(
  private val nomisApiService: CSIPNomisApiService,
  private val mappingApiService: CSIPMappingService,
  private val csipService: CSIPDpsApiService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun csipFactorInserted(event: CSIPFactorEvent) {
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

    val nomisFactorResponse = nomisApiService.getCSIPFactor(event.csipFactorId)
    // Get the report mapping
    mappingApiService.getCSIPReportByNomisId(nomisCSIPReportId = event.csipReportId)
      ?.let { reportMapping ->
        mappingApiService.getCSIPFactorByNomisId(nomisCSIPFactorId = event.csipFactorId)
          ?.let {
            // Should never happen - factor should not exist in the mapping table
            telemetryClient.trackEvent(
              "csip-factor-synchronisation-created-ignored",
              telemetry + ("dpsCSIPFactorId" to it.dpsCSIPFactorId) + ("reason" to "CSIP Factor already mapped"),
            )
          }
          ?: run {
            // HAPPY PATH
            csipService.createCSIPFactor(
              reportMapping.dpsCSIPId,
              nomisFactorResponse.toDPSCreateFactorRequest(),
              nomisFactorResponse.createdBy,
            ).also {
              telemetry.put("dpsCSIPFactorId", it.factorUuid.toString())

              tryToCreateCSIPFactorMapping(nomisFactorResponse, it, telemetry)
                .also { mappingCreateResult ->
                  if (mappingCreateResult == MappingResponse.MAPPING_FAILED) telemetry.put("mapping", "initial-failure")
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

  suspend fun csipFactorUpdated(event: CSIPFactorEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCSIPReportId" to event.csipReportId,
        "offenderNo" to event.offenderIdDisplay,
        "nomisCSIPFactorId" to event.csipFactorId,
      )
    val nomisFactorResponse = nomisApiService.getCSIPFactor(event.csipFactorId)
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("csip-factor-synchronisation-updated-skipped", telemetry)
    } else {
      val mapping = mappingApiService.getCSIPFactorByNomisId(event.csipFactorId)
      if (mapping == null) {
        // Should never happen
        telemetryClient.trackEvent("csip-factor-synchronisation-updated-failed", telemetry)
        throw IllegalStateException("Received CSIP_FACTORS-UPDATED for factor that has never been created")
      } else {
        csipService.updateCSIPFactor(
          csipFactorId = mapping.dpsCSIPFactorId,
          nomisFactorResponse.toDPSUpdateFactorRequest(),
          updatedByUsername = nomisFactorResponse.lastModifiedBy ?: nomisFactorResponse.createdBy,
        )
        telemetryClient.trackEvent(
          "csip-factor-synchronisation-updated-success",
          telemetry + ("dpsCSIPFactorId" to mapping.dpsCSIPFactorId),
        )
      }
    }
  }

  suspend fun csipFactorDeleted(event: CSIPFactorEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCSIPReportId" to event.csipReportId,
        "offenderNo" to event.offenderIdDisplay,
        "nomisCSIPFactorId" to event.csipFactorId,
      )

    mappingApiService.getCSIPFactorByNomisId(nomisCSIPFactorId = event.csipFactorId)
      ?.let { mapping ->
        log.debug("Found csip factor mapping: {}", mapping)
        csipService.deleteCSIPFactor(mapping.dpsCSIPFactorId)
        tryToDeleteCSIPFactorMapping(mapping.dpsCSIPFactorId)
        telemetryClient.trackEvent(
          "csip-factor-synchronisation-deleted-success",
          telemetry + ("dpsCSIPFactorId" to mapping.dpsCSIPFactorId),
        )
      } ?: let {
      telemetryClient.trackEvent("csip-factor-synchronisation-deleted-ignored", telemetry)
    }
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
      log.error("Failed to create mapping for csip factor ids $mapping", e)
      queueService.sendMessage(
        messageType = RETRY_CSIP_FACTOR_SYNCHRONISATION_MAPPING,
        synchronisationType = SynchronisationType.CSIP,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
      return MappingResponse.MAPPING_FAILED
    }
  }
  suspend fun retryCreateCSIPFactorMapping(retryMessage: InternalMessage<CSIPFactorMappingDto>) {
    log.debug("CSIP - Retrying CSIP Factor Mapping after failure")
    mappingApiService.createCSIPFactorMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "csip-factor-mapping-created-synchronisation-success",
        retryMessage.telemetryAttributes,
      )
    }
  }
  private suspend fun tryToDeleteCSIPFactorMapping(dpsCSIPFactorId: String) = runCatching {
    mappingApiService.deleteCSIPFactorMappingByDPSId(dpsCSIPFactorId)
  }.onFailure { e ->
    telemetryClient.trackEvent("csip-factor-mapping-deleted-failed", mapOf("dpsCSIPFactorId" to dpsCSIPFactorId))
    log.warn("Unable to delete mapping for csip factor $dpsCSIPFactorId. Please delete manually", e)
  }
}
