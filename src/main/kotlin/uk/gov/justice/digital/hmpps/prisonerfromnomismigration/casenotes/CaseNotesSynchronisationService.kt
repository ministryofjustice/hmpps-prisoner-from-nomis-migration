package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType.LOCATIONS

@Service
class CaseNotesSynchronisationService(
  private val nomisApiService: CaseNotesNomisApiService,
  private val caseNotesMappingService: CaseNotesMappingApiService,
  private val caseNotesService: CaseNotesApiService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun caseNoteInserted(event: CaseNotesEvent) {
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("casenotes-synchronisation-skipped", event.toTelemetryProperties())
      return
    }
    val nomisCaseNote = nomisApiService.getCaseNote(event.caseNoteId)
    caseNotesService.upsertCaseNote(nomisCaseNote.toDPSCreateSyncCaseNote()).apply {
      tryToCreateCaseNoteMapping(
        event,
        this.caseNoteId!!,
      )
    }
  }

  suspend fun caseNoteUpdated(event: CaseNotesEvent) { // , mapping: CaseNoteMappingDto?) {
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("casenotes-synchronisation-skipped", event.toTelemetryProperties())
      return
    }
    val nomisCaseNote = nomisApiService.getCaseNote(event.caseNoteId)
    val mapping = caseNotesMappingService.getMappingGivenNomisId(event.caseNoteId)

    caseNotesService.upsertCaseNote(nomisCaseNote.toDPSUpdateCaseNote(mapping.dpsCaseNoteId)).apply {}
//     "caseNoteId").isEqualTo(casenote1.id)
//     "bookingId").isEqualTo(bookingId)
//     "caseNoteType.code").isEqualTo("ALL")
//     "caseNoteSubType.code").isEqualTo("SA")
//     "authorUsername").isEqualTo("JANE.NARK")
//     "prisonId").isEqualTo("BXI")
//     "caseNoteText").isEqualTo("A note")
//     "amended").isEqualTo("false")
//     "occurrenceDateTime") assertThat(LocalDateTime.parse(it)).isCloseTo(now, within(2, ChronoUnit.MINUTES)) }
  }

  suspend fun caseNoteDeleted(event: CaseNotesEvent) {
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("casenotes-synchronisation-skipped", event.toTelemetryProperties())
      return
    }
    val mapping = caseNotesMappingService.getMappingGivenNomisId(event.caseNoteId)
    caseNotesService.deleteCaseNote(mapping.dpsCaseNoteId)
    tryToDeleteMapping(mapping.dpsCaseNoteId)

    telemetryClient.trackEvent(
      "casenotes-deleted-synchronisation-success",
      event.toTelemetryProperties(mapping.dpsCaseNoteId),
    )
  }

  enum class MappingResponse {
    MAPPING_CREATED,
    MAPPING_FAILED,
  }

  suspend fun tryToCreateCaseNoteMapping(
    event: CaseNotesEvent,
    caseNoteId: String,
  ): MappingResponse {
    val mapping = CaseNoteMappingDto(
      dpsCaseNoteId = caseNoteId,
      nomisCaseNoteId = event.caseNoteId,
      nomisBookingId = event.bookingId!!,
      offenderNo = event.offenderIdDisplay!!,
      mappingType = NOMIS_CREATED,
    )
    try {
      caseNotesMappingService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<CaseNoteMappingDto>>() {},
      ).also {
        if (it.isError) {
          val duplicateErrorDetails = (it.errorResponse!!).moreInfo
          telemetryClient.trackEvent(
            "from-nomis-synch-casenote-duplicate",
            mapOf<String, String>(
              "duplicateDpsCaseNoteId" to duplicateErrorDetails.duplicate.dpsCaseNoteId,
              "duplicateNomisCaseNoteId" to duplicateErrorDetails.duplicate.nomisCaseNoteId.toString(),
              "existingDpsCaseNoteId" to duplicateErrorDetails.existing.dpsCaseNoteId,
              "existingNomisCaseNoteId" to duplicateErrorDetails.existing.nomisCaseNoteId.toString(),
            ),
            null,
          )
        }
      }
      return MappingResponse.MAPPING_CREATED
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for dpsCaseNote id $caseNoteId, nomisCaseNoteId ${event.caseNoteId}",
        e,
      )
      queueService.sendMessage(
        messageType = RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = LOCATIONS,
        message = mapping,
        telemetryAttributes = event.toTelemetryProperties(caseNoteId),
      )
      return MappingResponse.MAPPING_FAILED
    }
  }

  private suspend fun tryToDeleteMapping(dpsId: String) = runCatching {
    caseNotesMappingService.deleteMappingGivenDpsId(dpsId)
    telemetryClient.trackEvent("casenotes-deleted-mapping-success", mapOf("dpsCaseNoteId" to dpsId))
  }.onFailure { e ->
    telemetryClient.trackEvent("casenotes-deleted-mapping-failed", mapOf("dpsCaseNoteId" to dpsId))
    log.warn("Unable to delete mapping for dpsCaseNoteId=$dpsId. Please delete manually", e)
  }

  suspend fun retryCreateCaseNoteMapping(retryMessage: InternalMessage<CaseNoteMappingDto>) {
    caseNotesMappingService.createMapping(
      retryMessage.body,
      object : ParameterizedTypeReference<DuplicateErrorResponse<CaseNoteMappingDto>>() {},
    ).also {
      telemetryClient.trackEvent(
        "casenotes-mapping-created-synchronisation-success",
        retryMessage.telemetryAttributes,
      )
    }
  }
}

private fun CaseNotesEvent.toTelemetryProperties(
  dpsCaseNoteId: String? = null,
  mappingFailed: Boolean? = null,
) = mapOf(
  "nomisCaseNoteId" to this.caseNoteId.toString(),
  "offenderNo" to (this.offenderIdDisplay ?: ""),
) + (dpsCaseNoteId?.let { mapOf("dpsCaseNoteId" to it) } ?: emptyMap()) + (
  mappingFailed?.let { mapOf("mapping" to "initial-failure") } ?: emptyMap()
  )
