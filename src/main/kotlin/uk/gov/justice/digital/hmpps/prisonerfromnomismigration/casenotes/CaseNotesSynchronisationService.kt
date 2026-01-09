package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesSynchronisationService.MappingResponse.MAPPING_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesSynchronisationService.MappingResponse.MAPPING_FAILED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.model.MoveCaseNotesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerBookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerMergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.originatesInDps
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto.MappingType.DPS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CaseNoteMappingDto.MappingType.NOMIS_CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseNoteResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.UpdateAmendment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.UpdateCaseNoteRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType.CASENOTES
import java.util.*

@Service
class CaseNotesSynchronisationService(
  private val nomisApiService: CaseNotesNomisApiService,
  private val caseNotesMappingService: CaseNotesMappingApiService,
  private val caseNotesByPrisonerMigrationMappingApiService: CaseNotesByPrisonerMigrationMappingApiService,
  private val caseNotesService: CaseNotesApiService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun caseNoteInserted(event: CaseNotesEvent) {
    try {
      val nomisCaseNote = nomisApiService.getCaseNote(event.caseNoteId)

      // We should be able to check this without having to get data from Nomis: this will help verify that:
      if (nomisCaseNote.originatesInDps() != event.isSourcedFromDPS()) {
        log.warn(
          "Detected casenote source discrepancy event {}, casenote {}",
          event.toString(),
          nomisCaseNote.toStringSelective(),
        )
      }

      if (nomisCaseNote.originatesInDps()) {
        telemetryClient.trackEvent("casenotes-synchronisation-created-skipped", event.toTelemetryProperties())
        return
      }
      if (nomisCaseNote.auditMissing()) {
        caseNotesMappingService.getMappingGivenNomisIdOrNull(event.caseNoteId)
          ?.apply {
            if (mappingType == DPS_CREATED) {
              // Detected where the auditModuleName is null but the mapping exists, signifying that this CN was created from DPS
              telemetryClient.trackEvent("casenotes-synchronisation-created-skipped-null", event.toTelemetryProperties())
              return
            }
          }
      }

      caseNotesService.upsertCaseNote(nomisCaseNote.toDPSSyncCaseNote(event.offenderIdDisplay!!)).apply {
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
    if (nomisCaseNote.originatesInDps()) {
      telemetryClient.trackEvent("casenotes-synchronisation-updated-skipped", event.toTelemetryProperties())
      return
    }

    try {
      updateDps(event, nomisCaseNote)
        ?.also { mapping ->
          updateRelatedNomisCaseNotes(mapping, event, nomisCaseNote)
          // Note that if this fails, the queue retry will update DPS again as well as retrying the related Nomis CNs,
          // but this is idempotent so is ok

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
      ?: run {
        telemetryClient.trackEvent(
          "casenotes-synchronisation-updated-mapping-failed",
          event.toTelemetryProperties(),
        )
        throw IllegalStateException("NO mapping found updating $nomisCaseNote")
      }
  }

  private suspend fun updateDps(
    event: CaseNotesEvent,
    nomisCaseNote: CaseNoteResponse,
  ): CaseNoteMappingDto? = caseNotesMappingService.getMappingGivenNomisIdOrNull(event.caseNoteId)
    ?.also { mapping ->
      caseNotesService.upsertCaseNote(
        nomisCaseNote.toDPSSyncCaseNote(
          event.offenderIdDisplay!!,
          UUID.fromString(mapping.dpsCaseNoteId),
        ),
      )
    }

  private suspend fun updateRelatedNomisCaseNotes(
    mapping: CaseNoteMappingDto,
    event: CaseNotesEvent,
    nomisCaseNote: CaseNoteResponse,
  ) {
    caseNotesMappingService.getByDpsId(mapping.dpsCaseNoteId)
      .filter { it.nomisCaseNoteId != event.caseNoteId }
      .map { otherNomisCaseNote ->
        nomisApiService.updateCaseNote(
          otherNomisCaseNote.nomisCaseNoteId,
          UpdateCaseNoteRequest(
            text = nomisCaseNote.caseNoteText,
            amendments = nomisCaseNote.amendments.map {
              UpdateAmendment(
                text = it.text,
                authorUsername = it.authorUsername,
                createdDateTime = it.createdDateTime,
              )
            },
          ),
        )
        telemetryClient.trackEvent(
          "casenotes-synchronisation-updated-related-success",
          mapOf(
            "nomisCaseNoteId" to otherNomisCaseNote.nomisCaseNoteId.toString(),
            "offenderNo" to event.offenderIdDisplay.toString(),
            "bookingId" to otherNomisCaseNote.nomisBookingId.toString(),
            "dpsCaseNoteId" to mapping.dpsCaseNoteId,
          ),
        )
      }
  }

  suspend fun repairDeletedCaseNote(nomisCaseNoteId: Long) = caseNoteDeleted(CaseNotesEvent(caseNoteId = nomisCaseNoteId))

  suspend fun caseNoteDeleted(event: CaseNotesEvent) {
    try {
      caseNotesMappingService.getMappingGivenNomisIdOrNull(event.caseNoteId)
        ?.also { mapping ->
          caseNotesMappingService.getByDpsId(mapping.dpsCaseNoteId)
            .filter { it.nomisCaseNoteId != event.caseNoteId }
            .forEach {
              nomisApiService.deleteCaseNote(it.nomisCaseNoteId)
              telemetryClient.trackEvent(
                "casenotes-synchronisation-deleted-related-success",
                mapOf(
                  "dpsCaseNoteId" to mapping.dpsCaseNoteId,
                  "nomisCaseNoteId" to it.nomisCaseNoteId.toString(),
                  "bookingId" to mapping.nomisBookingId.toString(),
                ),
              )
            }

          caseNotesService.deleteCaseNote(mapping.dpsCaseNoteId)
          caseNotesMappingService.deleteMappingGivenDpsId(mapping.dpsCaseNoteId)
          // Some syncs have separate telemetry for mapping failure but casenotes deletions are extremely rare

          telemetryClient.trackEvent(
            "casenotes-synchronisation-deleted-success",
            event.toTelemetryProperties(mapping.dpsCaseNoteId),
          )
        }
        ?: telemetryClient.trackEvent(
          "casenotes-deleted-synchronisation-skipped",
          event.toTelemetryProperties(),
        )
    } catch (e: Exception) {
      telemetryClient.trackEvent(
        "casenotes-synchronisation-deleted-failed",
        event.toTelemetryProperties() + mapOf("error" to (e.message ?: "unknown error")),
      )
      log.warn(
        "Unable to delete mapping for prisoner ${event.offenderIdDisplay} nomisCaseNoteId=${event.caseNoteId}. Please delete manually",
        e,
      )
    }
  }

  suspend fun synchronisePrisonerMerged(prisonerMergeEvent: PrisonerMergeDomainEvent) {
    /*

Some of the current booking's case notes have audit_module_name = 'MERGE'
Work out the old booking id as being that which has copies of these case notes
The affected existing mappings are those for the old prisoner and booking id for which just the prisoner is corrected .
Also add new mappings for the new booking id for the copied case notes, which point to the same DPS CNs as the unaffected existing mappings.

     */
    val (nomsNumber, removedNomsNumber, bookingId) = prisonerMergeEvent.additionalInformation
    try {
      val nomisCaseNotes = nomisApiService.getCaseNotesForPrisoner(nomsNumber).caseNotes
      val freshlyMergedNomisCaseNotes = nomisCaseNotes
        .filter { isMergeCaseNoteRecentlyCreated(it, prisonerMergeEvent) }
      // It is now ok for this list to be empty - it means the 'no longer copy' Nomis fix has been deployed

      val existingMappings = caseNotesMappingService.getMappings(nomisCaseNotes.map { it.caseNoteId })
        .associateBy { it.nomisCaseNoteId }

      // Skip if mappings already created
      if (freshlyMergedNomisCaseNotes.isNotEmpty() && existingMappings.size == nomisCaseNotes.size) {
        telemetryClient.trackEvent(
          "casenotes-prisoner-merge-skipped",
          mapOf(
            "offenderNo" to nomsNumber,
            "removedOffenderNo" to removedNomsNumber,
            "bookingId" to bookingId,
          ),
        )
        return
      }

      // mapping of freshly merged case notes to what the note is a copy of
      val newToOldMap = freshlyMergedNomisCaseNotes
        .associate { newCaseNote ->
          newCaseNote.caseNoteId to nomisCaseNotes.first { old -> isMergeCopy(old, newCaseNote) }
        }

      caseNotesMappingService.updateMappingsByNomisId(removedNomsNumber, nomsNumber)

      if (freshlyMergedNomisCaseNotes.isNotEmpty()) {
        val newMappings =
          freshlyMergedNomisCaseNotes.map { newNomisCaseNote ->
            CaseNoteMappingDto(
              dpsCaseNoteId = existingMappings[newToOldMap[newNomisCaseNote.caseNoteId]?.caseNoteId]
                ?.dpsCaseNoteId
                ?: throw IllegalStateException("synchronisePrisonerMerged(): No mapping found for newNomisCaseNote = $newNomisCaseNote, offender $nomsNumber"),
              nomisCaseNoteId = newNomisCaseNote.caseNoteId,
              nomisBookingId = bookingId,
              offenderNo = nomsNumber,
              mappingType = NOMIS_CREATED,
            )
          }

        // Now exclude any mappings that already exist.  This can happen if two merges occur within half an hour of each
        // other as we find all merged case notes within that time frame
        val newUniqueMappings = newMappings.filter { it.nomisCaseNoteId !in existingMappings.keys }
        if (newUniqueMappings.size != newMappings.size) {
          telemetryClient.trackEvent(
            "casenotes-prisoner-merge-existing-mappings",
            mapOf(
              "offenderNo" to nomsNumber,
              "removedOffenderNo" to removedNomsNumber,
              "bookingId" to bookingId,
              "mappingsCount" to (newMappings.size - newUniqueMappings.size),
              "mappings" to newMappings.filter { it.nomisCaseNoteId in existingMappings.keys }
                .joinToString { it.nomisCaseNoteId.toString() },
            ),
          )
        }

        caseNotesByPrisonerMigrationMappingApiService.createMappings(
          newUniqueMappings,
          object : ParameterizedTypeReference<DuplicateErrorResponse<CaseNoteMappingDto>>() {},
        ).also {
          if (it.isError) {
            val duplicateErrorDetails = (it.errorResponse!!).moreInfo
            telemetryClient.trackEvent(
              "nomis-migration-casenotes-duplicate",
              mapOf<String, String>(
                "offenderNo" to nomsNumber,
                "duplicateDpsCaseNoteId" to duplicateErrorDetails.duplicate.dpsCaseNoteId,
                "duplicateNomisBookingId" to duplicateErrorDetails.duplicate.nomisBookingId.toString(),
                "duplicateNomisCaseNoteId" to duplicateErrorDetails.duplicate.nomisCaseNoteId.toString(),
                "existingDpsCaseNoteId" to duplicateErrorDetails.existing.dpsCaseNoteId,
                "existingNomisBookingId" to duplicateErrorDetails.existing.nomisBookingId.toString(),
                "existingNomisCaseNoteId" to duplicateErrorDetails.existing.nomisCaseNoteId.toString(),
              ),
              null,
            )
          }
        }
      }

      telemetryClient.trackEvent(
        "casenotes-prisoner-merge",
        mapOf(
          "offenderNo" to nomsNumber,
          "removedOffenderNo" to removedNomsNumber,
          "bookingId" to bookingId,
          "freshlyMergedNomisCaseNotesCount" to freshlyMergedNomisCaseNotes.size,
        ),
      )
    } catch (e: Exception) {
      telemetryClient.trackEvent(
        "casenotes-prisoner-merge-failed",
        mapOf(
          "offenderNo" to nomsNumber,
          "removedOffenderNo" to removedNomsNumber,
          "bookingId" to bookingId,
          "error" to (e.message ?: "unknown error"),
        ),
      )
      throw e
    }
  }

  private fun isMergeCaseNoteRecentlyCreated(
    response: CaseNoteResponse,
    prisonerMergeEvent: PrisonerMergeDomainEvent,
  ): Boolean = response.auditModuleName == "MERGE" &&
    response.createdDatetime.isAfter(prisonerMergeEvent.occurredAt.toLocalDateTime().minusMinutes(30))

  suspend fun synchronisePrisonerBookingMoved(prisonerMergeEvent: PrisonerBookingMovedDomainEvent) {
    val (movedToNomsNumber, movedFromNomsNumber, bookingId) = prisonerMergeEvent.additionalInformation

    try {
      val caseNotes = caseNotesMappingService.updateMappingsByBookingId(bookingId, movedToNomsNumber)
      val caseNotesToResynchronise = caseNotes.map { UUID.fromString(it.dpsCaseNoteId) }.toSet()
      caseNotesService.moveCaseNotes(
        MoveCaseNotesRequest(
          fromPersonIdentifier = movedFromNomsNumber,
          toPersonIdentifier = movedToNomsNumber,
          caseNoteIds = caseNotesToResynchronise,
        ),
      )

      telemetryClient.trackEvent(
        "casenotes-booking-moved",
        mapOf(
          "bookingId" to bookingId,
          "movedToNomsNumber" to movedToNomsNumber,
          "movedFromNomsNumber" to movedFromNomsNumber,
          "count" to caseNotesToResynchronise.size,
        ),
      )
    } catch (e: Exception) {
      telemetryClient.trackEvent(
        "casenotes-booking-moved-failed",
        mapOf(
          "bookingId" to bookingId,
          "movedToNomsNumber" to movedToNomsNumber,
          "movedFromNomsNumber" to movedFromNomsNumber,
          "error" to (e.message ?: "unknown error"),
        ),
      )
      throw e
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
            mapOf(
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
  "offenderNo" to this.offenderIdDisplay.toString(),
  "bookingId" to this.bookingId.toString(),
) + (dpsCaseNoteId?.let { mapOf("dpsCaseNoteId" to it) } ?: emptyMap()) + (
  if (mappingFailed == true) mapOf("mapping" to "initial-failure") else emptyMap()
  )

private fun CaseNoteResponse.originatesInDps() = auditModuleName.originatesInDps()
private fun CaseNoteResponse.auditMissing() = auditModuleName == null
private fun CaseNotesEvent.isSourcedFromDPS() = auditModuleName == "DPS_SYNCHRONISATION"
private fun CaseNoteResponse.toStringSelective() = "(caseNoteId=$caseNoteId, bookingId=$bookingId, sourceSystem=$sourceSystem)"

private fun isMergeCopy(
  response: CaseNoteResponse,
  mergeCopiedCaseNote: CaseNoteResponse,
): Boolean = response.creationDateTime == mergeCopiedCaseNote.creationDateTime &&
  response.caseNoteText == mergeCopiedCaseNote.caseNoteText &&
  response.bookingId != mergeCopiedCaseNote.bookingId &&
  response.auditModuleName != "MERGE" &&
  response.caseNoteType == mergeCopiedCaseNote.caseNoteType &&
  response.caseNoteSubType == mergeCopiedCaseNote.caseNoteSubType &&
  response.occurrenceDateTime == mergeCopiedCaseNote.occurrenceDateTime &&
  response.authorStaffId == mergeCopiedCaseNote.authorStaffId &&
  response.amendments.size == mergeCopiedCaseNote.amendments.size
