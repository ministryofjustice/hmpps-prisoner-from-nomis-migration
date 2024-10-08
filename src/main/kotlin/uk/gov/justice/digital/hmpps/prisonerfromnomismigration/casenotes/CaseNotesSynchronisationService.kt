package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesSynchronisationService.MappingResponse.MAPPING_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesSynchronisationService.MappingResponse.MAPPING_FAILED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType.CASENOTES
import java.util.UUID

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
    val nomisCaseNote = nomisApiService.getCaseNote(event.caseNoteId)
    if (nomisCaseNote.isSourcedFromDPS()) {
      telemetryClient.trackEvent("casenotes-synchronisation-created-skipped", event.toTelemetryProperties())
      return
    }

    try {
      caseNotesService.upsertCaseNote(nomisCaseNote.toDPSSyncCaseNote(event.offenderIdDisplay)).apply {
        tryToCreateCaseNoteMapping(
          event,
          this.id.toString(),
        ).also { mappingCreateResult ->
          telemetryClient.trackEvent(
            "casenotes-synchronisation-created-success",
            event.toTelemetryProperties(
              dpsCaseNoteId = this.id.toString(),
              mappingFailed = mappingCreateResult == MAPPING_FAILED,
            ),
          )
        }
      }
    } catch (e: Exception) {
      telemetryClient.trackEvent(
        "casenotes-synchronisation-created-failed",
        event.toTelemetryProperties() + mapOf("error" to (e.message ?: "unknown error")),
      )
      throw e
    }
  }

  suspend fun caseNoteUpdated(event: CaseNotesEvent) {
    val nomisCaseNote = nomisApiService.getCaseNote(event.caseNoteId)
    if (nomisCaseNote.isSourcedFromDPS()) {
      telemetryClient.trackEvent("casenotes-synchronisation-updated-skipped", event.toTelemetryProperties())
      return
    }
    val mapping =
      try {
        caseNotesMappingService.getMappingGivenNomisIdOrNull(event.caseNoteId)
          ?.also { mapping ->
            caseNotesService.upsertCaseNote(
              nomisCaseNote.toDPSSyncCaseNote(
                event.offenderIdDisplay,
                UUID.fromString(mapping.dpsCaseNoteId),
              ),
            )
            telemetryClient.trackEvent(
              "casenotes-synchronisation-updated-success",
              event.toTelemetryProperties(mapping.dpsCaseNoteId),
            )
          }
      } catch (e: Exception) {
        telemetryClient.trackEvent(
          "casenotes-synchronisation-updated-failed",
          event.toTelemetryProperties() + mapOf("error" to (e.message ?: "unknown error")),
        )
        throw e
      }

    if (mapping == null) {
      telemetryClient.trackEvent(
        "casenotes-synchronisation-updated-mapping-failed",
        event.toTelemetryProperties(),
      )
      throw IllegalStateException("NO mapping found updating $nomisCaseNote")
    }
  }

  suspend fun caseNoteDeleted(event: CaseNotesEvent) {
    try {
      caseNotesMappingService.getMappingGivenNomisIdOrNull(event.caseNoteId)
        ?.also { mapping ->
          caseNotesService.deleteCaseNote(mapping.dpsCaseNoteId)
          caseNotesMappingService.deleteMappingGivenDpsId(mapping.dpsCaseNoteId)
          // Some syncs have separate telemetry for mapping failure but casenotes deletions are extremely rare

          telemetryClient.trackEvent(
            "casenotes-synchronisation-deleted-success",
            event.toTelemetryProperties(mapping.dpsCaseNoteId),
          )
        }
        ?: telemetryClient.trackEvent(
          "casenotes-deleted-synchronisation-skipped", event.toTelemetryProperties(),
        )
    } catch (e: Exception) {
      telemetryClient.trackEvent(
        "casenotes-synchronisation-deleted-failed",
        event.toTelemetryProperties() + mapOf("error" to (e.message ?: "unknown error")),
      )
      log.warn("Unable to delete mapping for prisoner ${event.offenderIdDisplay} nomisCaseNoteId=${event.caseNoteId}. Please delete manually", e)
    }
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
      nomisBookingId = event.bookingId ?: 0,
      offenderNo = event.offenderIdDisplay,
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
          )
        }
      }
      return MAPPING_CREATED
    } catch (e: Exception) {
      log.error(
        "Failed to create mapping for dpsCaseNote id $caseNoteId, nomisCaseNoteId ${event.caseNoteId}",
        e,
      )
      queueService.sendMessage(
        messageType = RETRY_SYNCHRONISATION_MAPPING.name,
        synchronisationType = CASENOTES,
        message = mapping,
        telemetryAttributes = event.toTelemetryProperties(caseNoteId),
      )
      return MAPPING_FAILED
    }
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
  "offenderNo" to this.offenderIdDisplay,
  "bookingId" to this.bookingId.toString(),
) + (dpsCaseNoteId?.let { mapOf("dpsCaseNoteId" to it) } ?: emptyMap()) + (
  if (mappingFailed == true) mapOf("mapping" to "initial-failure") else emptyMap()
  )

private fun CaseNoteResponse.isSourcedFromDPS() = auditModuleName == "DPS_SYNCHRONISATION"
