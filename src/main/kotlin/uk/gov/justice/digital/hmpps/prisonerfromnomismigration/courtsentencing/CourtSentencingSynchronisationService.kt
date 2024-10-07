package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceAllMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseAllMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentenceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SentenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_COURT_APPEARANCE_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_COURT_CASE_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_COURT_CHARGE_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_SENTENCE_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class CourtSentencingSynchronisationService(
  private val mappingApiService: CourtSentencingMappingApiService,
  private val nomisApiService: CourtSentencingNomisApiService,
  private val dpsApiService: CourtSentencingDpsApiService,
  private val queueService: SynchronisationQueueService,
  private val telemetryClient: TelemetryClient,
  @Value("\${courtsentencing.has-migrated-data:false}")
  private val hasMigratedAllData: Boolean,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun nomisCourtCaseInserted(event: CourtCaseEvent) {
    val telemetry =
      mapOf(
        "nomisCourtCaseId" to event.caseId.toString(),
        "offenderNo" to event.offenderIdDisplay,
        "nomisBookingId" to event.bookingId.toString(),
      )
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("court-case-synchronisation-created-skipped", telemetry)
    } else {
      val nomisCourtCase =
        nomisApiService.getCourtCase(offenderNo = event.offenderIdDisplay, courtCaseId = event.caseId)
      mappingApiService.getCourtCaseOrNullByNomisId(event.caseId)?.let { mapping ->
        telemetryClient.trackEvent(
          "court-case-synchronisation-created-ignored",
          telemetry + ("dpsCourtCaseId" to mapping.dpsCourtCaseId),
        )
      } ?: let {
        dpsApiService.createCourtCase(nomisCourtCase.toDpsCourtCase()).run {
          tryToCreateMapping(
            nomisCourtCase = nomisCourtCase,
            dpsCourtCaseResponse = this,
            telemetry = telemetry + ("dpsCourtCaseId" to this.courtCaseUuid),
          ).also { mappingCreateResult ->
            val mappingSuccessTelemetry =
              (if (mappingCreateResult == MappingResponse.MAPPING_CREATED) mapOf() else mapOf("mapping" to "initial-failure"))
            val additionalTelemetry = mappingSuccessTelemetry + ("dpsCourtCaseId" to this.courtCaseUuid)

            telemetryClient.trackEvent(
              "court-case-synchronisation-created-success",
              telemetry + additionalTelemetry,
            )
          }
        }
      }
    }
  }

  suspend fun nomisCourtAppearanceInserted(event: CourtAppearanceEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCourtAppearanceId" to event.eventId.toString(),
        "offenderNo" to event.offenderIdDisplay,
        "nomisBookingId" to event.bookingId.toString(),
      )
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("court-appearance-synchronisation-created-skipped", telemetry)
    } else {
      val nomisCourtAppearance =
        nomisApiService.getCourtAppearance(
          offenderNo = event.offenderIdDisplay,
          courtAppearanceId = event.eventId,
        )
      mappingApiService.getCourtAppearanceOrNullByNomisId(event.eventId)?.let { mapping ->
        telemetryClient.trackEvent(
          "court-appearance-synchronisation-created-ignored",
          telemetry + ("dpsCourtAppearanceId" to mapping.dpsCourtAppearanceId) + ("reason" to "appearance already mapped"),
        )
      } ?: let {
        // only dealing with appearances associated with a court case, COURT_EVENTS are created by movements also
        if (isAppearancePartOfACourtCase(nomisCourtAppearance)) {
          mappingApiService.getCourtCaseOrNullByNomisId(nomisCourtAppearance.caseId!!)?.let { courtCaseMapping ->
            telemetry.put("nomisCourtCaseId", courtCaseMapping.nomisCourtCaseId.toString())
            telemetry.put("dpsCourtCaseId", courtCaseMapping.dpsCourtCaseId)
            // TODO wire up caseref from list of identifiers when implemented
            dpsApiService.createCourtAppearance(
              nomisCourtAppearance.toDpsCourtAppearance(
                event.offenderIdDisplay,
                courtCaseMapping.dpsCourtCaseId,
              ),
            ).run {
              telemetry.put("dpsCourtAppearanceId", this.appearanceUuid.toString())
              tryToCreateCourtAppearanceMapping(
                nomisCourtAppearance = nomisCourtAppearance,
                dpsCourtAppearanceResponse = this,
                telemetry,
              ).also { mappingCreateResult ->
                if (mappingCreateResult == MappingResponse.MAPPING_FAILED) telemetry.put("mapping", "initial-failure")
                telemetryClient.trackEvent(
                  "court-appearance-synchronisation-created-success",
                  telemetry,
                )
              }
            }
          } ?: let {
            telemetryClient.trackEvent(
              "court-appearance-synchronisation-created-failed",
              telemetry + ("nomisCourtCaseId" to nomisCourtAppearance.caseId.toString()) + ("reason" to "associated court case is not mapped"),
            )
            throw IllegalStateException("Received COURT_EVENTS_INSERTED for court case that has never been created/mapped")
          }
        } else {
          telemetryClient.trackEvent(
            "court-appearance-synchronisation-created-ignored",
            telemetry + ("reason" to "appearance not associated with a court case, for example generated as part of a movement"),
          )
        }
      }
    }
  }

  private suspend fun isAppearancePartOfACourtCase(nomisAppearance: CourtEventResponse) =
    nomisAppearance.caseId?.let { true } ?: false

  suspend fun nomisCourtCaseUpdated(event: CourtCaseEvent) {
    val telemetry =
      mapOf(
        "nomisBookingId" to event.bookingId.toString(),
        "nomisCourtCaseId" to event.caseId.toString(),
        "offenderNo" to event.offenderIdDisplay,
      )
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("court-case-synchronisation-updated-skipped", telemetry)
    } else {
      val mapping = mappingApiService.getCourtCaseOrNullByNomisId(event.caseId)
      if (mapping == null) {
        telemetryClient.trackEvent(
          "court-case-synchronisation-updated-failed",
          telemetry,
        )
        throw IllegalStateException("Received OFFENDER_CASES-UPDATED for court-case that has never been created")
      } else {
        val nomisCourtCase =
          nomisApiService.getCourtCase(offenderNo = event.offenderIdDisplay, courtCaseId = event.caseId)
        dpsApiService.updateCourtCase(
          courtCaseId = mapping.dpsCourtCaseId,
          nomisCourtCase.toDpsCourtCase(),
        )
        telemetryClient.trackEvent(
          "court-case-synchronisation-updated-success",
          telemetry + ("dpsCourtCaseId" to mapping.dpsCourtCaseId),
        )
      }
    }
  }

  suspend fun nomisCourtCaseDeleted(event: CourtCaseEvent) {
    val telemetry =
      mapOf(
        "nomisCourtCaseId" to event.caseId,
        "offenderNo" to event.offenderIdDisplay,
        "nomisBookingId" to event.bookingId,
      )
    val mapping = mappingApiService.getCourtCaseOrNullByNomisId(event.caseId)
    if (mapping == null) {
      telemetryClient.trackEvent(
        "court-case-synchronisation-deleted-ignored",
        telemetry,
      )
    } else {
      dpsApiService.deleteCourtCase(courtCaseId = mapping.dpsCourtCaseId)
      tryToDeleteCourtCaseMapping(mapping.dpsCourtCaseId)
      telemetryClient.trackEvent(
        "court-case-synchronisation-deleted-success",
        telemetry + ("dpsCourtCaseId" to mapping.dpsCourtCaseId),
      )
    }
  }

  private suspend fun tryToDeleteCourtCaseMapping(dpsCourtCaseId: String) = runCatching {
    mappingApiService.deleteCourtCaseMappingByDpsId(dpsCourtCaseId)
  }.onFailure { e ->
    telemetryClient.trackEvent("court-case-mapping-deleted-failed", mapOf("dpsCourtCaseId" to dpsCourtCaseId))
    log.warn("Unable to delete mapping for court case $dpsCourtCaseId. Please delete manually", e)
  }

  private suspend fun tryToCreateMapping(
    nomisCourtCase: CourtCaseResponse,
    dpsCourtCaseResponse: CreateCourtCaseResponse,
    telemetry: Map<String, Any>,
  ): MappingResponse {
    val mapping = CourtCaseAllMappingDto(
      dpsCourtCaseId = dpsCourtCaseResponse.courtCaseUuid,
      nomisCourtCaseId = nomisCourtCase.id,
      mappingType = CourtCaseAllMappingDto.MappingType.NOMIS_CREATED,
      courtCharges = emptyList(),
      courtAppearances = emptyList(),
    )
    try {
      mappingApiService.createMapping(
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<CourtCaseAllMappingDto>>() {},
      ).also {
        if (it.isError) {
          val duplicateErrorDetails = (it.errorResponse!!).moreInfo
          telemetryClient.trackEvent(
            "from-nomis-sync-court-case-duplicate",
            mapOf<String, String>(
              "duplicateDpsCourtCaseId" to duplicateErrorDetails.duplicate.dpsCourtCaseId,
              "duplicateNomisCourtCaseId" to duplicateErrorDetails.duplicate.nomisCourtCaseId.toString(),
              "existingDpsCourtCaseId" to duplicateErrorDetails.existing.dpsCourtCaseId,
              "existingNomisCourtCaseId" to duplicateErrorDetails.existing.nomisCourtCaseId.toString(),
            ),
            null,
          )
        }
      }
      return MappingResponse.MAPPING_CREATED
    } catch (e: Exception) {
      log.error("Failed to create mapping for court case ids $mapping", e)
      queueService.sendMessage(
        messageType = RETRY_COURT_CASE_SYNCHRONISATION_MAPPING,
        synchronisationType = SynchronisationType.COURT_SENTENCING,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
      return MappingResponse.MAPPING_FAILED
    }
  }

  private suspend fun tryToCreateCourtAppearanceMapping(
    nomisCourtAppearance: CourtEventResponse,
    dpsCourtAppearanceResponse: CreateCourtAppearanceResponse,
    telemetry: Map<String, Any>,
  ): MappingResponse {
    val mapping = CourtAppearanceAllMappingDto(
      dpsCourtAppearanceId = dpsCourtAppearanceResponse.appearanceUuid.toString(),
      nomisCourtAppearanceId = nomisCourtAppearance.id,
      mappingType = CourtAppearanceAllMappingDto.MappingType.NOMIS_CREATED,
      courtCharges = emptyList(),
    )
    try {
      mappingApiService.createCourtAppearanceMapping(
        mapping,
      ).also {
        if (it.isError) {
          val duplicateErrorDetails = (it.errorResponse!!).moreInfo
          telemetryClient.trackEvent(
            "from-nomis-sync-court-appearance-duplicate",
            mapOf<String, String>(
              "duplicateDpsCourtAppearanceId" to duplicateErrorDetails.duplicate.dpsCourtAppearanceId,
              "duplicateNomisCourtAppearanceId" to duplicateErrorDetails.duplicate.nomisCourtAppearanceId.toString(),
              "existingDpsCourtAppearanceId" to duplicateErrorDetails.existing.dpsCourtAppearanceId,
              "existingNomisCourtAppearanceId" to duplicateErrorDetails.existing.nomisCourtAppearanceId.toString(),
            ),
            null,
          )
        }
      }
      return MappingResponse.MAPPING_CREATED
    } catch (e: Exception) {
      log.error("Failed to create mapping for court appearance ids $mapping", e)
      queueService.sendMessage(
        messageType = RETRY_COURT_APPEARANCE_SYNCHRONISATION_MAPPING,
        synchronisationType = SynchronisationType.COURT_SENTENCING,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
      return MappingResponse.MAPPING_FAILED
    }
  }

  private suspend fun tryToCreateChargeMapping(
    nomisOffenderCharge: OffenderChargeResponse,
    dpsChargeResponse: CreateNewChargeResponse,
    telemetry: Map<String, Any>,
  ): MappingResponse {
    val mapping = CourtChargeMappingDto(
      dpsCourtChargeId = dpsChargeResponse.chargeUuid.toString(),
      nomisCourtChargeId = nomisOffenderCharge.id,
      mappingType = CourtChargeMappingDto.MappingType.NOMIS_CREATED,
    )
    try {
      mappingApiService.createCourtChargeMapping(
        mapping,
      ).also {
        if (it.isError) {
          val duplicateErrorDetails = (it.errorResponse!!).moreInfo
          telemetryClient.trackEvent(
            "from-nomis-sync-charge-duplicate",
            mapOf<String, String>(
              "duplicateDpsCourtChargeId" to duplicateErrorDetails.duplicate.dpsCourtChargeId,
              "duplicateNomisCourtChargeId" to duplicateErrorDetails.duplicate.nomisCourtChargeId.toString(),
              "existingDpsCourtChargeId" to duplicateErrorDetails.existing.dpsCourtChargeId,
              "existingNomisCourtChargeId" to duplicateErrorDetails.existing.nomisCourtChargeId.toString(),
            ),
            null,
          )
        }
      }
      return MappingResponse.MAPPING_CREATED
    } catch (e: Exception) {
      log.error("Failed to create mapping for court Charge ids $mapping", e)
      queueService.sendMessage(
        messageType = RETRY_COURT_CHARGE_SYNCHRONISATION_MAPPING,
        synchronisationType = SynchronisationType.COURT_SENTENCING,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
      return MappingResponse.MAPPING_FAILED
    }
  }

  private suspend fun tryToCreateSentenceMapping(
    nomisSentence: SentenceResponse,
    dpsSentenceResponse: CreateSentenceResponse,
    telemetry: Map<String, Any>,
  ): MappingResponse {
    val mapping = SentenceMappingDto(
      dpsSentenceId = dpsSentenceResponse.sentenceUuid,
      nomisSentenceSequence = nomisSentence.sentenceSeq.toInt(),
      nomisBookingId = nomisSentence.bookingId,
      mappingType = SentenceMappingDto.MappingType.NOMIS_CREATED,
    )
    try {
      mappingApiService.createSentenceMapping(
        mapping,
      ).also {
        if (it.isError) {
          val duplicateErrorDetails = (it.errorResponse!!).moreInfo
          telemetryClient.trackEvent(
            "from-nomis-sync-sentence-duplicate",
            mapOf<String, String>(
              "duplicateDpsSentenceId" to duplicateErrorDetails.duplicate.dpsSentenceId,
              "duplicateNomisSentenceSequence" to duplicateErrorDetails.duplicate.nomisSentenceSequence.toString(),
              "duplicateNomisBookingId" to duplicateErrorDetails.duplicate.nomisBookingId.toString(),
              "existingDpsSentenceId" to duplicateErrorDetails.existing.dpsSentenceId,
              "existingNomisSentenceSequence" to duplicateErrorDetails.existing.nomisSentenceSequence.toString(),
              "existingNomisBookingId" to duplicateErrorDetails.existing.nomisBookingId.toString(),
            ),
            null,
          )
        }
      }
      return MappingResponse.MAPPING_CREATED
    } catch (e: Exception) {
      log.error("Failed to create mapping for sentence ids $mapping", e)
      queueService.sendMessage(
        messageType = RETRY_SENTENCE_SYNCHRONISATION_MAPPING,
        synchronisationType = SynchronisationType.COURT_SENTENCING,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
      return MappingResponse.MAPPING_FAILED
    }
  }

  suspend fun nomisCourtAppearanceUpdated(event: CourtAppearanceEvent) {
    val telemetry =
      mapOf(
        "nomisBookingId" to event.bookingId.toString(),
        "nomisCourtAppearanceId" to event.eventId.toString(),
        "offenderNo" to event.offenderIdDisplay,
      )
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("court-appearance-synchronisation-updated-skipped", telemetry)
    } else {
      val mapping = mappingApiService.getCourtAppearanceOrNullByNomisId(event.eventId)
      if (mapping == null) {
        telemetryClient.trackEvent(
          "court-appearance-synchronisation-updated-failed",
          telemetry,
        )
        throw IllegalStateException("Received COURT_EVENTS-UPDATED for court appearance that has never been created")
      } else {
        val nomisCourtAppearance = nomisApiService.getCourtAppearance(
          offenderNo = event.offenderIdDisplay,
          courtAppearanceId = event.eventId,
        )
        // only dealing with appearances associated with a court case, COURT_EVENTS are created by movements also
        if (isAppearancePartOfACourtCase(nomisCourtAppearance)) {
          mappingApiService.getCourtCaseOrNullByNomisId(nomisCourtAppearance.caseId!!)?.let { courtCaseMapping ->
            // TODO DPS have yet to implement an update - expecting a new update DTO without a caseId
            dpsApiService.updateCourtAppearance(
              courtAppearanceId = mapping.dpsCourtAppearanceId,
              nomisCourtAppearance.toDpsCourtAppearance(offenderNo = event.offenderIdDisplay, dpsCaseId = "DUMMY"),
            )
            telemetryClient.trackEvent(
              "court-appearance-synchronisation-updated-success",
              telemetry + ("dpsCourtAppearanceId" to mapping.dpsCourtAppearanceId),
            )
          } ?: let {
            telemetryClient.trackEvent(
              "court-appearance-synchronisation-updated-failed",
              telemetry + ("nomisCourtCaseId" to nomisCourtAppearance.caseId.toString()) + ("reason" to "associated court case is not mapped"),
            )
            throw IllegalStateException("Received COURT_EVENTS_UPDATED with court case ${nomisCourtAppearance.caseId} that has never been created/mapped")
          }
        } else {
          telemetryClient.trackEvent(
            "court-appearance-synchronisation-updated-ignored",
            telemetry + ("reason" to "appearance not associated with a court case, for example generated as part of a movement"),
          )
        }
      }
    }
  }

  suspend fun nomisCourtAppearanceDeleted(event: CourtAppearanceEvent) {
    val telemetry =
      mapOf(
        "nomisCourtAppearanceId" to event.eventId,
        "offenderNo" to event.offenderIdDisplay,
        "nomisBookingId" to event.bookingId,
      )
    val mapping = mappingApiService.getCourtAppearanceOrNullByNomisId(event.eventId)
    if (mapping == null) {
      telemetryClient.trackEvent(
        "court-appearance-synchronisation-deleted-ignored",
        telemetry,
      )
    } else {
      dpsApiService.deleteCourtAppearance(courtAppearanceId = mapping.dpsCourtAppearanceId)
      tryToDeleteCourtAppearanceMapping(mapping.dpsCourtAppearanceId)
      telemetryClient.trackEvent(
        "court-appearance-synchronisation-deleted-success",
        telemetry + ("dpsCourtAppearanceId" to mapping.dpsCourtAppearanceId),
      )
    }
  }

  // New Court event charge.
  // is it a new underlying offender charge? 2 DPS endpoints - create charge or just apply to appearance
  suspend fun nomisCourtChargeInserted(event: CourtEventChargeEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCourtAppearanceId" to event.eventId.toString(),
        "nomisOffenderChargeId" to event.chargeId.toString(),
        "offenderNo" to event.offenderIdDisplay,
        "nomisBookingId" to event.bookingId.toString(),
      )
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("court-charge-synchronisation-created-skipped", telemetry)
    } else {
      // Check court appearance is mapped and throw exception to retry if not
      mappingApiService.getCourtAppearanceOrNullByNomisId(event.eventId)?.let { courtAppearanceMapping ->
        telemetry.put("dpsCourtAppearanceId", courtAppearanceMapping.dpsCourtAppearanceId)
        val nomisOffenderCharge =
          nomisApiService.getOffenderCharge(
            offenderNo = event.offenderIdDisplay,
            offenderChargeId = event.chargeId,
          )

        mappingApiService.getOffenderChargeOrNullByNomisId(event.chargeId)?.let { mapping ->
          // mapping means this is an existing offender charge to be applied to the appearance
          telemetry.put("dpsChargeId", mapping.dpsCourtChargeId)
          telemetry.put("existingDpsCharge", "true")
          dpsApiService.associateExistingCourtCharge(
            courtAppearanceMapping.dpsCourtAppearanceId,
            nomisOffenderCharge.toDpsCharge(mapping.dpsCourtChargeId),
          )
        } ?: let {
          // no mapping means this is a new offender charge to be created and applied to the appearance
          telemetry.put("existingDpsCharge", "false")
          dpsApiService.addNewCourtCharge(
            courtAppearanceId = courtAppearanceMapping.dpsCourtAppearanceId,
            nomisOffenderCharge.toDpsCharge(),
          ).run {
            telemetry.put("dpsChargeId", this.chargeUuid.toString())
            tryToCreateChargeMapping(
              nomisOffenderCharge = nomisOffenderCharge,
              dpsChargeResponse = this,
              telemetry,
            ).also { mappingCreateResult ->
              if (mappingCreateResult == MappingResponse.MAPPING_FAILED) telemetry.put("mapping", "initial-failure")
            }
          }
        }
        telemetryClient.trackEvent(
          "court-charge-synchronisation-created-success",
          telemetry,
        )
      } ?: let {
        telemetryClient.trackEvent(
          "court-charge-synchronisation-created-failed",
          telemetry,
        )
        if (hasMigratedAllData) {
          // after migration has run this should not happen so make sure this message goes in DLQ
          throw IllegalStateException("Received COURT_EVENT_CHARGES-INSERTED for court appearance ${event.eventId} that has never been created")
        }
      }
    }
  }

  // This is a deleting of the association of a nomis charge and court appearance. Not the actual deletion of the underlying charge
  suspend fun nomisCourtChargeDeleted(event: CourtEventChargeEvent) {
    val telemetry =
      mutableMapOf(
        "nomisCourtAppearanceId" to event.eventId.toString(),
        "nomisOffenderChargeId" to event.chargeId.toString(),
        "offenderNo" to event.offenderIdDisplay,
        "nomisBookingId" to event.bookingId.toString(),
      )
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("court-charge-synchronisation-deleted-skipped", telemetry)
    } else {
      mappingApiService.getCourtAppearanceOrNullByNomisId(event.eventId)?.let { courtAppearanceMapping ->
        telemetry["dpsCourtAppearanceId"] = courtAppearanceMapping.dpsCourtAppearanceId

        mappingApiService.getOffenderChargeOrNullByNomisId(event.chargeId)?.let { chargeMapping ->
          telemetry.put("dpsChargeId", chargeMapping.dpsCourtChargeId)
          dpsApiService.removeCourtCharge(
            courtAppearanceId = courtAppearanceMapping.dpsCourtAppearanceId,
            chargeId = chargeMapping.dpsCourtChargeId,
          ).also {
            // check with nomis to see if offender_charge has been deleted
            nomisApiService.getOffenderChargeOrNull(
              offenderNo = event.offenderIdDisplay,
              offenderChargeId = event.chargeId,
            ) ?: let {
              tryToDeleteCourtChargeMapping(chargeMapping)
            }
          }
          telemetryClient.trackEvent(
            "court-charge-synchronisation-deleted-success",
            telemetry,
          )
        } ?: let {
          // TODO determine whether retry is the best option here
          logFailureAndThrowError(
            telemetry,
            "court-charge-synchronisation-deleted-failed",
            "Received COURT_EVENT_CHARGES-DELETED for court charge ${event.chargeId} that does not have a mapping",
          )
        }
      } ?: let {
        logFailureAndThrowError(
          telemetry,
          "court-charge-synchronisation-deleted-failed",
          "Received COURT_EVENT_CHARGES-DELETED for court appearance ${event.eventId} without an appearance mapping",
        )
      }
    }
  }

  suspend fun nomisOffenderChargeUpdated(event: OffenderChargeEvent) {
    val telemetry =
      mapOf(
        "nomisBookingId" to event.bookingId.toString(),
        "nomisOffenderChargeId" to event.chargeId.toString(),
        "offenderNo" to event.offenderIdDisplay,
      )
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("court-charge-synchronisation-updated-skipped", telemetry)
    } else {
      val mapping = mappingApiService.getOffenderChargeOrNullByNomisId(event.chargeId)
      if (mapping == null) {
        telemetryClient.trackEvent(
          "court-charge-synchronisation-updated-failed",
          telemetry,
        )
        if (hasMigratedAllData) {
          // after migration has run this should not happen so make sure this message goes in DLQ
          throw IllegalStateException("Received OFFENDER_CHARGES-UPDATED for charge that has never been mapped")
        }
      } else {
        val nomisCourtCase = nomisApiService.getOffenderCharge(
          offenderNo = event.offenderIdDisplay,
          offenderChargeId = event.chargeId,
        )
        // TODO DPS have yet to implement an update - expecting a new update DTO without a caseId
        dpsApiService.updateCourtCharge(
          chargeId = mapping.dpsCourtChargeId,
          nomisCourtCase.toDpsCharge(chargeId = mapping.dpsCourtChargeId),
        )
        telemetryClient.trackEvent(
          "court-charge-synchronisation-updated-success",
          telemetry + ("dpsChargeId" to mapping.dpsCourtChargeId),
        )
      }
    }
  }

  private fun logFailureAndThrowError(
    telemetry: MutableMap<String, String>,
    eventName: String,
    errorMessage: String,
    overrideMigrationFlag: Boolean = true,
  ) {
    telemetryClient.trackEvent(
      eventName,
      telemetry,
    )
    if (overrideMigrationFlag || hasMigratedAllData) {
      // after migration has run this should not happen so make sure this message goes in DLQ
      throw IllegalStateException(errorMessage)
    }
  }

  private suspend fun tryToDeleteCourtAppearanceMapping(dpsCourtAppearanceId: String) = runCatching {
    mappingApiService.deleteCourtAppearanceMappingByDpsId(dpsCourtAppearanceId)
  }.onFailure { e ->
    telemetryClient.trackEvent(
      "court-appearance-mapping-deleted-failed",
      mapOf("dpsCourtAppearanceId" to dpsCourtAppearanceId),
    )
    log.warn("Unable to delete mapping for court appearance $dpsCourtAppearanceId. Please delete manually", e)
  }

  private suspend fun getDpsChargeMappings(nomisSentence: SentenceResponse): List<String> {
    return try {
      nomisSentence.offenderCharges.map {
        mappingApiService.getOffenderChargeByNomisId(it.id).dpsCourtChargeId
      }
    } catch (notFoundException: WebClientResponseException.NotFound) {
      telemetryClient.trackEvent(
        name = "charge-mapping-missing",
        properties = mutableMapOf(
          "nomisBookingId" to nomisSentence.bookingId.toString(),
          "nomisSentenceSequence" to nomisSentence.sentenceSeq.toString(),
        ),
      )
      log.error("Unable to find mapping for nomis offender charges in the context of sentence: bookingId= ${nomisSentence.bookingId} sentenceSequence= ${nomisSentence.sentenceSeq}}\nPossible causes: events out of order or offender charge has not been migrated")
      throw notFoundException
    }
  }

  private suspend fun tryToDeleteCourtChargeMapping(mapping: CourtChargeMappingDto) = runCatching {
    mappingApiService.deleteCourtChargeMappingByNomisId(mapping.nomisCourtChargeId)
    telemetryClient.trackEvent(
      "court-charge-mapping-deleted-success",
      mapOf("nomisOffenderCharge" to mapping.nomisCourtChargeId, "dpsCourtChargeId" to mapping.dpsCourtChargeId),
    )
  }.onFailure { e ->
    telemetryClient.trackEvent(
      "court-charge-mapping-deleted-failed",
      mapOf("nomisOffenderCharge" to mapping.nomisCourtChargeId, "dpsCourtChargeId" to mapping.dpsCourtChargeId),
    )
    log.warn(
      "Unable to delete mapping for court charge with nomis id: $mapping.nomisCourtChargeId. Please delete manually",
      e,
    )
  }

  suspend fun retryCreateCourtCaseMapping(retryMessage: InternalMessage<CourtCaseAllMappingDto>) {
    mappingApiService.createMapping(
      retryMessage.body,
      object : ParameterizedTypeReference<DuplicateErrorResponse<CourtCaseAllMappingDto>>() {},
    ).also {
      telemetryClient.trackEvent(
        "court-case-mapping-created-synchronisation-success",
        retryMessage.telemetryAttributes,
      )
    }
  }

  suspend fun retryCreateCourtAppearanceMapping(retryMessage: InternalMessage<CourtAppearanceAllMappingDto>) {
    mappingApiService.createCourtAppearanceMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "court-appearance-mapping-created-synchronisation-success",
        retryMessage.telemetryAttributes,
      )
    }
  }

  suspend fun nomisSentenceInserted(event: OffenderSentenceEvent) {
    val telemetry =
      mapOf(
        "nomisSentenceSequence" to event.sentenceSequence.toString(),
        "offenderNo" to event.offenderIdDisplay,
        "nomisBookingId" to event.bookingId.toString(),
      )
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("sentence-synchronisation-created-skipped", telemetry)
    } else {
      val nomisSentence =
        nomisApiService.getOffenderSentence(bookingId = event.bookingId, sentenceSequence = event.sentenceSequence)
      mappingApiService.getSentenceOrNullByNomisId(event.bookingId, sentenceSequence = event.sentenceSequence)
        ?.let { mapping ->
          telemetryClient.trackEvent(
            "sentence-synchronisation-created-ignored",
            telemetry,
          )
        } ?: let {
        // retrieve offence mappings (created as part of the court appearance flow)
        dpsApiService.createSentence(
          nomisSentence.toDpsSentence(
            event.offenderIdDisplay,
            getDpsChargeMappings(nomisSentence),
          ),
        ).run {
          tryToCreateSentenceMapping(
            nomisSentence = nomisSentence,
            dpsSentenceResponse = this,
            telemetry = telemetry + ("dpsSentenceId" to this.sentenceUuid),
          ).also { mappingCreateResult ->
            val mappingSuccessTelemetry =
              (if (mappingCreateResult == MappingResponse.MAPPING_CREATED) mapOf() else mapOf("mapping" to "initial-failure"))
            val additionalTelemetry = mappingSuccessTelemetry + ("dpsSentenceId" to this.sentenceUuid)

            telemetryClient.trackEvent(
              "sentence-synchronisation-created-success",
              telemetry + additionalTelemetry,
            )
          }
        }
      }
    }
  }

  suspend fun nomisSentenceDeleted(event: OffenderSentenceEvent) {
    val telemetry =
      mapOf(
        "nomisSentenceSequence" to event.sentenceSequence,
        "offenderNo" to event.offenderIdDisplay,
        "nomisBookingId" to event.bookingId,
      )
    val mapping = mappingApiService.getSentenceOrNullByNomisId(
      sentenceSequence = event.sentenceSequence,
      bookingId = event.bookingId,
    )
    if (mapping == null) {
      telemetryClient.trackEvent(
        "sentence-synchronisation-deleted-ignored",
        telemetry,
      )
    } else {
      dpsApiService.deleteSentence(sentenceId = mapping.dpsSentenceId)
      tryToDeleteSentenceMapping(mapping.dpsSentenceId)
      telemetryClient.trackEvent(
        "sentence-synchronisation-deleted-success",
        telemetry + ("dpsSentenceId" to mapping.dpsSentenceId),
      )
    }
  }

  private suspend fun tryToDeleteSentenceMapping(dpsSentenceId: String) = runCatching {
    mappingApiService.deleteSentenceMappingByDpsId(dpsSentenceId)
  }.onFailure { e ->
    telemetryClient.trackEvent("sentence-mapping-deleted-failed", mapOf("dpsSentenceId" to dpsSentenceId))
    log.warn("Unable to delete mapping for sentence with dps Id $dpsSentenceId. Please delete manually", e)
  }

  suspend fun nomisSentenceUpdated(event: OffenderSentenceEvent) {
    val telemetry =
      mapOf(
        "nomisBookingId" to event.bookingId.toString(),
        "nomisSentenceSequence" to event.sentenceSequence.toString(),
        "offenderNo" to event.offenderIdDisplay,
      )
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("sentence-synchronisation-updated-skipped", telemetry)
    } else {
      val mapping = mappingApiService.getSentenceOrNullByNomisId(
        bookingId = event.bookingId,
        sentenceSequence = event.sentenceSequence,
      )
      if (mapping == null) {
        telemetryClient.trackEvent(
          "sentence-synchronisation-updated-failed",
          telemetry,
        )
        if (hasMigratedAllData) {
          // after migration has run this should not happen so make sure this message goes in DLQ
          throw IllegalStateException("Received OFFENDER_SENTENCES-UPDATED for sentence that has never been created")
        }
      } else {
        val nomisSentence =
          nomisApiService.getOffenderSentence(bookingId = event.bookingId, sentenceSequence = event.sentenceSequence)
        dpsApiService.updateSentence(
          sentenceId = mapping.dpsSentenceId,
          nomisSentence.toDpsSentence(offenderNo = event.offenderIdDisplay, getDpsChargeMappings(nomisSentence)),
        )
        telemetryClient.trackEvent(
          "sentence-synchronisation-updated-success",
          telemetry + ("dpsSentenceId" to mapping.dpsSentenceId),
        )
      }
    }
  }

  suspend fun nomisCaseIdentifiersUpdated(eventName: String, event: CaseIdentifiersEvent) {
    val telemetry =
      mapOf(
        "nomisIdentifiersNo" to event.identifierNo,
        "nomisIdentifiersType" to event.identifierType,
        "nomisCourtCaseId" to event.caseId.toString(),
        "offenderNo" to event.offenderIdDisplay,
        "eventType" to eventName,
      )
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("case-identifiers-synchronisation-skipped", telemetry)
    } else {
      val mapping = mappingApiService.getCourtCaseOrNullByNomisId(event.caseId)
      if (mapping == null) {
        telemetryClient.trackEvent(
          "case-identifiers-synchronisation-failed",
          telemetry,
        )
        throw IllegalStateException("Received OFFENDER_CASE_IDENTIFIERS event to for court-case without a mapping")
      } else {
        val nomisCourtCase =
          nomisApiService.getCourtCase(offenderNo = event.offenderIdDisplay, courtCaseId = event.caseId)
        dpsApiService.refreshCaseIdentifiers(
          courtCaseId = mapping.dpsCourtCaseId,
          caseReferences = nomisCourtCase.caseInfoNumbers.map { it.toDpsCaseReference() },
        )
        telemetryClient.trackEvent(
          "case-identifiers-synchronisation-success",
          telemetry + ("dpsCourtCaseId" to mapping.dpsCourtCaseId),
        )
      }
    }
  }

  suspend fun retryCreateCourtChargeMapping(retryMessage: InternalMessage<CourtChargeMappingDto>) {
    mappingApiService.createCourtChargeMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "court-charge-mapping-created-synchronisation-success",
        retryMessage.telemetryAttributes,
      )
    }
  }

  suspend fun retryCreateSentenceMapping(retryMessage: InternalMessage<SentenceMappingDto>) {
    mappingApiService.createSentenceMapping(
      retryMessage.body,
    ).also {
      telemetryClient.trackEvent(
        "sentence-mapping-created-synchronisation-success",
        retryMessage.telemetryAttributes,
      )
    }
  }
}

private enum class MappingResponse {
  MAPPING_CREATED,
  MAPPING_FAILED,
}
