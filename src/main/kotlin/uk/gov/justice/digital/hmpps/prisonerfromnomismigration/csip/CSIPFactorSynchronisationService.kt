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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_CSIP_FACTOR_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class CSIPFactorSynchronisationService(
  private val nomisApiService: CSIPNomisApiService,
  private val mappingApiService: CSIPMappingService,
  private val csipService: CSIPService,
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
    mappingApiService.findCSIPReportByNomisId(nomisCSIPReportId = event.csipReportId)
      ?.let { reportMapping ->
        mappingApiService.findCSIPFactorByNomisId(nomisCSIPFactorId = event.csipFactorId)
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
              nomisFactorResponse.toDPSFactorRequest(),
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

  suspend fun csipFactorDeleted(event: CSIPFactorEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCSIPReportId" to event.csipReportId,
        "offenderNo" to event.offenderIdDisplay,
        "nomisCSIPFactorId" to event.csipFactorId,
      )

    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("csip-factor-synchronisation-deleted-skipped", telemetry)
      return
    }

    mappingApiService.findCSIPFactorByNomisId(nomisCSIPFactorId = event.csipFactorId)
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

  private suspend fun tryToDeleteCSIPFactorMapping(dpsCSIPFactorId: String) = runCatching {
    mappingApiService.deleteCSIPFactorMappingByDPSId(dpsCSIPFactorId)
  }.onFailure { e ->
    telemetryClient.trackEvent("csip-factor-mapping-deleted-failed", mapOf("dpsCSIPFactorId" to dpsCSIPFactorId))
    log.warn("Unable to delete mapping for csip report $dpsCSIPFactorId. Please delete manually", e)
  }
}
