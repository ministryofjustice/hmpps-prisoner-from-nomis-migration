package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CourtCaseLegacyData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyChargeCreatedResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCourtAppearanceCreatedResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCourtCaseCreatedResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacySentenceCreatedResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyUpdateWholeCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.ParentEntityNotFoundRetry
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceAllMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseAllMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentenceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceResponse
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
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val DPS_CASE_REFERENCE = "CASE/INFO#"
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
        dpsApiService.createCourtCase(nomisCourtCase.toLegacyDpsCourtCase()).run {
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
    if (isAppearancePartOfACourtCase(event)) {
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
          mappingApiService.getCourtCaseOrNullByNomisId(nomisCourtAppearance.caseId!!)?.let { courtCaseMapping ->
            telemetry["nomisCourtCaseId"] = courtCaseMapping.nomisCourtCaseId.toString()
            telemetry["dpsCourtCaseId"] = courtCaseMapping.dpsCourtCaseId
            dpsApiService.createCourtAppearance(
              nomisCourtAppearance.toDpsCourtAppearance(dpsCaseId = courtCaseMapping.dpsCourtCaseId),
            ).run {
              telemetry["dpsCourtAppearanceId"] = this.lifetimeUuid.toString()
              tryToCreateCourtAppearanceMapping(
                nomisCourtAppearance = nomisCourtAppearance,
                dpsCourtAppearanceResponse = this,
                telemetry,
              ).also { mappingCreateResult ->
                if (mappingCreateResult == MappingResponse.MAPPING_FAILED) telemetry["mapping"] = "initial-failure"
              }
            }
            telemetryClient.trackEvent(
              "court-appearance-synchronisation-created-success",
              telemetry,
            )
          } ?: let {
            telemetryClient.trackEvent(
              "court-appearance-synchronisation-created-failed",
              telemetry + ("nomisCourtCaseId" to nomisCourtAppearance.caseId.toString()) + ("reason" to "associated court case is not mapped"),
            )
            throw ParentEntityNotFoundRetry("Received COURT_EVENTS_INSERTED for court case ${nomisCourtAppearance.caseId} that has never been created/mapped")
          }
        }
      }
    } else {
      telemetryClient.trackEvent(
        "court-appearance-synchronisation-created-ignored",
        telemetry + ("reason" to "appearance not associated with a court case, for example generated as part of a movement"),
      )
    }
  }

  private suspend fun isAppearancePartOfACourtCase(appearanceEvent: CourtAppearanceEvent) = appearanceEvent.caseId?.let { true } ?: false

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
          nomisCourtCase.toLegacyDpsCourtCase(),
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
      mappingApiService.deleteCourtCaseMappingByDpsId(mapping.dpsCourtCaseId)
      telemetryClient.trackEvent(
        "court-case-synchronisation-deleted-success",
        telemetry + ("dpsCourtCaseId" to mapping.dpsCourtCaseId),
      )
    }
  }

  private suspend fun tryToCreateMapping(
    nomisCourtCase: CourtCaseResponse,
    dpsCourtCaseResponse: LegacyCourtCaseCreatedResponse,
    telemetry: Map<String, Any>,
  ): MappingResponse {
    val mapping = CourtCaseAllMappingDto(
      dpsCourtCaseId = dpsCourtCaseResponse.courtCaseUuid,
      nomisCourtCaseId = nomisCourtCase.id,
      mappingType = CourtCaseAllMappingDto.MappingType.NOMIS_CREATED,
      courtCharges = emptyList(),
      courtAppearances = emptyList(),
      sentences = emptyList(),
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
    dpsCourtAppearanceResponse: LegacyCourtAppearanceCreatedResponse,
    telemetry: Map<String, Any>,
  ): MappingResponse {
    val mapping = CourtAppearanceAllMappingDto(
      dpsCourtAppearanceId = dpsCourtAppearanceResponse.lifetimeUuid.toString(),
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
    dpsChargeResponse: LegacyChargeCreatedResponse,
    telemetry: Map<String, Any>,
  ): MappingResponse {
    val mapping = CourtChargeMappingDto(
      dpsCourtChargeId = dpsChargeResponse.lifetimeUuid.toString(),
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
    dpsSentenceResponse: LegacySentenceCreatedResponse,
    telemetry: Map<String, Any>,
  ): MappingResponse {
    val mapping = SentenceMappingDto(
      dpsSentenceId = dpsSentenceResponse.lifetimeUuid.toString(),
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
    if (isAppearancePartOfACourtCase(event)) {
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
          mappingApiService.getCourtCaseOrNullByNomisId(nomisCourtAppearance.caseId!!)?.let { courtCaseMapping ->
            dpsApiService.updateCourtAppearance(
              courtAppearanceId = mapping.dpsCourtAppearanceId,
              nomisCourtAppearance.toDpsCourtAppearance(dpsCaseId = courtCaseMapping.dpsCourtCaseId),
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
        }
      }
    } else {
      telemetryClient.trackEvent(
        "court-appearance-synchronisation-updated-ignored",
        telemetry + ("reason" to "appearance not associated with a court case, for example generated as part of a movement"),
      )
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
      mappingApiService.deleteCourtAppearanceMappingByDpsId(mapping.dpsCourtAppearanceId)
      telemetryClient.trackEvent(
        "court-appearance-synchronisation-deleted-success",
        telemetry + ("dpsCourtAppearanceId" to mapping.dpsCourtAppearanceId),
      )
    }
  }

  // New Court event charge.
  // is it a new underlying offender charge? 2 DPS endpoints - create charge or apply new version to appearance
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
        telemetry["dpsCourtAppearanceId"] = courtAppearanceMapping.dpsCourtAppearanceId
        mappingApiService.getOffenderChargeOrNullByNomisId(event.chargeId)?.let { mapping ->
          // mapping means this is an existing offender charge to be applied to the appearance
          telemetry["dpsChargeId"] = mapping.dpsCourtChargeId
          telemetry["existingDpsCharge"] = "true"
          val nomisCourtEventCharge =
            nomisApiService.getCourtEventCharge(
              offenderNo = event.offenderIdDisplay,
              offenderChargeId = event.chargeId,
              eventId = event.eventId,
            )
          dpsApiService.associateExistingCourtCharge(
            courtAppearanceMapping.dpsCourtAppearanceId,
            mapping.dpsCourtChargeId,
            nomisCourtEventCharge.toDpsCharge(),
          )
        } ?: let {
          val nomisOffenderCharge =
            nomisApiService.getOffenderCharge(
              offenderNo = event.offenderIdDisplay,
              offenderChargeId = event.chargeId,
            )
          // no mapping means this is a new offender charge to be created and applied to the appearance
          telemetry["existingDpsCharge"] = "false"
          dpsApiService.addNewCourtCharge(
            nomisOffenderCharge.toDpsCharge(courtAppearanceMapping.dpsCourtAppearanceId),
          ).run {
            telemetry["dpsChargeId"] = this.lifetimeUuid.toString()
            tryToCreateChargeMapping(
              nomisOffenderCharge = nomisOffenderCharge,
              dpsChargeResponse = this,
              telemetry,
            ).also { mappingCreateResult ->
              if (mappingCreateResult == MappingResponse.MAPPING_FAILED) telemetry["mapping"] = "initial-failure"
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
          telemetry + ("reason" to "court appearance is not mapped"),
        )
        // after migration has run this should not happen so make sure this message goes in DLQ
        throw ParentEntityNotFoundRetry("Received COURT_EVENT_CHARGES-INSERTED for court appearance ${event.eventId} that has never been created")
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
          telemetry["dpsChargeId"] = chargeMapping.dpsCourtChargeId
          dpsApiService.removeCourtChargeAssociation(
            courtAppearanceId = courtAppearanceMapping.dpsCourtAppearanceId,
            chargeId = chargeMapping.dpsCourtChargeId,
          ).also {
            // check with nomis to see if offender_charge has been deleted
            nomisApiService.getOffenderChargeOrNull(
              offenderNo = event.offenderIdDisplay,
              offenderChargeId = event.chargeId,
            ) ?: tryToDeleteCourtChargeMapping(chargeMapping)
          }
          telemetryClient.trackEvent(
            "court-charge-synchronisation-deleted-success",
            telemetry,
          )
        } ?: let {
          telemetryClient.trackEvent(
            "court-charge-synchronisation-deleted-skipped",
            telemetry + ("reason" to "charge is not mapped"),
          )
        }
      } ?: let {
        telemetryClient.trackEvent(
          "court-charge-synchronisation-deleted-skipped",
          telemetry + ("reason" to "court appearance is not mapped"),
        )
      }
    }
  }

  suspend fun nomisCourtChargeUpdated(event: CourtEventChargeEvent) {
    var telemetry =
      mapOf(
        "nomisBookingId" to event.bookingId.toString(),
        "nomisOffenderChargeId" to event.chargeId.toString(),
        "nomisCourtAppearanceId" to event.eventId.toString(),
        "offenderNo" to event.offenderIdDisplay,
      )
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("court-charge-synchronisation-updated-skipped", telemetry)
    } else {
      mappingApiService.getCourtAppearanceOrNullByNomisId(event.eventId)
        ?.let { courtAppearanceMapping ->
          mappingApiService.getOffenderChargeOrNullByNomisId(event.chargeId)?.let { chargeMapping ->
            nomisApiService.getCourtEventCharge(
              offenderNo = event.offenderIdDisplay,
              eventId = event.eventId,
              offenderChargeId = event.chargeId,
            ).let { nomisCourtAppearanceCharge ->
              telemetry = telemetry + ("dpsCourtAppearanceId" to courtAppearanceMapping.dpsCourtAppearanceId)
              dpsApiService.updateCourtCharge(
                chargeId = chargeMapping.dpsCourtChargeId,
                appearanceId = courtAppearanceMapping.dpsCourtAppearanceId,
                charge = nomisCourtAppearanceCharge.toDpsCharge(),
              )
              telemetryClient.trackEvent(
                "court-charge-synchronisation-updated-success",
                telemetry + ("dpsChargeId" to chargeMapping.dpsCourtChargeId),
              )
            }
          } ?: let {
            telemetryClient.trackEvent(
              "court-charge-synchronisation-updated-failed",
              telemetry + ("reason" to "charge is not mapped"),
            )
            throw ParentEntityNotFoundRetry("Received OFFENDER_CHARGES_UPDATED for charge ${event.chargeId} has never been mapped")
          }
        } ?: let {
        telemetryClient.trackEvent(
          "court-charge-synchronisation-updated-failed",
          telemetry + ("reason" to "associated court appearance is not mapped"),
        )
        throw ParentEntityNotFoundRetry("Received COURT_EVENT_CHARGES_UPDATED with court appearance ${event.eventId} that has never been created/mapped")
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
    // we are only interested in offence code changes as these are the only changes that are not picked up by CEC updates
    if (event.offenceCodeChange) {
      if (event.auditModuleName == "DPS_SYNCHRONISATION") {
        telemetryClient.trackEvent(
          "court-charge-synchronisation-updated-skipped",
          telemetry + ("reason" to "Change originates from DPS"),
        )
      } else {
        mappingApiService.getOffenderChargeOrNullByNomisId(event.chargeId)?.let { chargeMapping ->
          nomisApiService.getOffenderCharge(
            offenderNo = event.offenderIdDisplay,
            offenderChargeId = event.chargeId,
          ).let { nomisOffenderCharge ->
            dpsApiService.updateChargeOffence(
              chargeId = chargeMapping.dpsCourtChargeId,
              charge = LegacyUpdateWholeCharge(
                offenceCode = nomisOffenderCharge.offence.offenceCode,
              ),
            )
            telemetryClient.trackEvent(
              "court-charge-synchronisation-updated-success",
              telemetry + ("dpsChargeId" to chargeMapping.dpsCourtChargeId) + ("reason" to "offence code changed"),
            )
          }
        } ?: let {
          telemetryClient.trackEvent(
            "court-charge-synchronisation-updated-failed",
            telemetry + ("nomisOffenderChargeId" to event.chargeId.toString()) + ("reason" to "charge is not mapped"),
          )
          throw IllegalStateException("Received OFFENDER_CHARGES_UPDATED for charge ${event.chargeId} has never been mapped")
        }
      }
    } else {
      telemetryClient.trackEvent(
        "court-charge-synchronisation-updated-skipped",
        telemetry + ("reason" to "OFFENDER_CHARGES-UPDATED change is not an offence code change"),
      )
    }
  }

  private suspend fun getDpsChargeMappings(nomisSentence: SentenceResponse): List<String> = try {
    nomisSentence.offenderCharges.map {
      mappingApiService.getOffenderChargeByNomisId(it.id).dpsCourtChargeId
    }
  } catch (webResponseException: WebClientResponseException) {
    telemetryClient.trackEvent(
      name = "charge-mapping-missing",
      properties = mutableMapOf(
        "nomisBookingId" to nomisSentence.bookingId.toString(),
        "nomisSentenceSequence" to nomisSentence.sentenceSeq.toString(),
      ),
    )
    log.error("Unable to find mapping for nomis offender charges in the context of sentence: bookingId= ${nomisSentence.bookingId} sentenceSequence= ${nomisSentence.sentenceSeq}}\nPossible causes: events out of order or offender charge has not been migrated")
    throw ParentEntityNotFoundRetry("Unable to find mapping for nomis offender charges in the context of sentence: bookingId= ${nomisSentence.bookingId} sentenceSequence= ${nomisSentence.sentenceSeq}")
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
        "nomisSentenceSequence" to event.sentenceSeq.toString(),
        "nomisSentenceCategory" to event.sentenceCategory,
        "nomisSentenceLevel" to event.sentenceLevel,
        "offenderNo" to event.offenderIdDisplay,
        "nomisCaseId" to event.caseId.toString(),
        "nomisBookingId" to event.bookingId.toString(),
      )
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("sentence-synchronisation-created-skipped", telemetry)
    } else {
      if (isSentenceInScope(event)) {
        val caseId = event.caseId!!
        mappingApiService.getSentenceOrNullByNomisId(event.bookingId, sentenceSequence = event.sentenceSeq)
          ?.let {
            telemetryClient.trackEvent(
              "sentence-synchronisation-created-ignored",
              telemetry + ("reason" to "sentence mapping exists"),
            )
          } ?: let {
          mappingApiService.getCourtCaseOrNullByNomisId(caseId) ?: let {
            telemetryClient.trackEvent(
              "sentence-synchronisation-created-failed",
              telemetry + ("reason" to "Nomis court case $caseId is not mapped"),
            )
            throw ParentEntityNotFoundRetry("Received OFFENDER_SENTENCES-INSERTED for sentence seq ${event.sentenceSeq} and booking ${event.bookingId} on a case ${event.caseId} that has never been created/mapped")
          }
          val nomisSentence =
            nomisApiService.getOffenderSentence(offenderNo = event.offenderIdDisplay, caseId = caseId, sentenceSequence = event.sentenceSeq)
          // retrieve offence mappings (created as part of the court appearance flow)
          dpsApiService.createSentence(
            nomisSentence.toDpsSentence(
              sentenceChargeIds = getDpsChargeMappings(nomisSentence),
              dpsConsecUuid = nomisSentence.consecSequence?.let {
                getConsecutiveSequenceMappingOrThrow(
                  event = event,
                  consecSequence = it,
                )
              },
            ),
          ).run {
            log.info("Created sentence with dps response $this")
            tryToCreateSentenceMapping(
              nomisSentence = nomisSentence,
              dpsSentenceResponse = this,
              telemetry = telemetry + ("dpsSentenceId" to this.lifetimeUuid) + ("dpsSentenceTerms" to this.createdPeriodLengths.toString()),
            ).also { mappingCreateResult ->
              val mappingSuccessTelemetry =
                (if (mappingCreateResult == MappingResponse.MAPPING_CREATED) mapOf() else mapOf("mapping" to "initial-failure"))
              val additionalTelemetry = mappingSuccessTelemetry + ("dpsSentenceId" to this.lifetimeUuid)

              telemetryClient.trackEvent(
                "sentence-synchronisation-created-success",
                telemetry + additionalTelemetry,
              )
            }
          }
        }
      } else {
        telemetryClient.trackEvent(
          "sentence-synchronisation-created-ignored",
          telemetry + ("reason" to "sentence not in scope"),
        )
      }
    }
  }

  private suspend fun getConsecutiveSequenceMappingOrThrow(
    event: OffenderSentenceEvent,
    consecSequence: Int,
  ): String = mappingApiService.getSentenceOrNullByNomisId(
    bookingId = event.bookingId,
    sentenceSequence = consecSequence,
  )?.dpsSentenceId
    ?: throw ParentEntityNotFoundRetry("Consecutive Sentence with sequence $consecSequence has not been mapped. For sentence sequence ${event.sentenceSeq} and booking ${event.bookingId}")

  suspend fun nomisSentenceDeleted(event: OffenderSentenceEvent) {
    val telemetry =
      mapOf(
        "nomisSentenceSequence" to event.sentenceSeq,
        "offenderNo" to event.offenderIdDisplay,
        "nomisBookingId" to event.bookingId,
        "nomisSentenceCategory" to event.sentenceCategory,
        "nomisSentenceLevel" to event.sentenceLevel,
        "nomisCaseId" to event.caseId.toString(),
      )
    if (isSentenceInScope(event)) {
      val mapping = mappingApiService.getSentenceOrNullByNomisId(
        sentenceSequence = event.sentenceSeq,
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
    } else {
      telemetryClient.trackEvent(
        "sentence-synchronisation-deleted-ignored",
        telemetry + ("reason" to "sentence not in scope"),
      )
    }
  }

  private suspend fun tryToDeleteSentenceMapping(dpsSentenceId: String) = runCatching {
    mappingApiService.deleteSentenceMappingByDpsId(dpsSentenceId)
  }.onFailure { e ->
    telemetryClient.trackEvent("sentence-mapping-deleted-failed", mapOf("dpsSentenceId" to dpsSentenceId))
    log.warn("Unable to delete mapping for sentence with dps Id $dpsSentenceId. Please delete manually", e)
  }

  suspend fun isSentenceInScope(sentenceEvent: OffenderSentenceEvent): Boolean = sentenceEvent.caseId != null && sentenceEvent.sentenceLevel == "IND" && sentenceEvent.sentenceCategory != "LICENCE"

  suspend fun nomisSentenceUpdated(event: OffenderSentenceEvent) {
    val telemetry =
      mapOf(
        "nomisBookingId" to event.bookingId.toString(),
        "nomisCaseId" to event.caseId.toString(),
        "nomisSentenceSequence" to event.sentenceSeq.toString(),
        "nomisSentenceCategory" to event.sentenceCategory,
        "nomisSentenceLevel" to event.sentenceLevel,
        "offenderNo" to event.offenderIdDisplay,
      )
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("sentence-synchronisation-updated-skipped", telemetry)
    } else {
      if (isSentenceInScope(event)) {
        val caseId = event.caseId!!
        val mapping = mappingApiService.getSentenceOrNullByNomisId(
          bookingId = event.bookingId,
          sentenceSequence = event.sentenceSeq,
        )
        if (mapping == null) {
          telemetryClient.trackEvent(
            "sentence-synchronisation-updated-failed",
            telemetry,
          )
          throw IllegalStateException("Received OFFENDER_SENTENCES-UPDATED for sentence (sequence ${event.sentenceSeq} booking ${event.bookingId}) that has never been created")
        } else {
          val nomisSentence =
            nomisApiService.getOffenderSentence(
              offenderNo = event.offenderIdDisplay,
              caseId = caseId,
              sentenceSequence = event.sentenceSeq,
            )
          dpsApiService.updateSentence(
            sentenceId = mapping.dpsSentenceId,
            nomisSentence.toDpsSentence(
              dpsConsecUuid = nomisSentence.consecSequence?.let {
                getConsecutiveSequenceMappingOrThrow(
                  event = event,
                  consecSequence = it,
                )
              },
              sentenceChargeIds = getDpsChargeMappings(nomisSentence),
            ),
          )
          telemetryClient.trackEvent(
            "sentence-synchronisation-updated-success",
            telemetry + ("dpsSentenceId" to mapping.dpsSentenceId),
          )
        }
      } else {
        telemetryClient.trackEvent(
          "sentence-synchronisation-updated-ignored",
          telemetry + ("reason" to "sentence not in scope"),
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
        "eventType" to eventName,
      )
    if (event.auditModuleName == "DPS_SYNCHRONISATION") {
      telemetryClient.trackEvent("case-identifiers-synchronisation-skipped", telemetry)
    } else {
      val mapping = mappingApiService.getCourtCaseOrNullByNomisId(event.caseId)

      if (mapping == null) {
        val isDelete = eventName == "OFFENDER_CASE_IDENTIFIERS-DELETED"
        if (!isDelete) {
          telemetryClient.trackEvent(
            "case-identifiers-synchronisation-failed",
            telemetry + ("isDelete" to isDelete.toString()),
          )
          throw IllegalStateException("Received OFFENDER_CASE_IDENTIFIERS event to for court-case without a mapping")
        } else {
          telemetryClient.trackEvent(
            "case-identifiers-synchronisation-skipped",
            telemetry + ("isDelete" to isDelete.toString()),
          )
        }
      } else {
        val nomisCourtCase =
          nomisApiService.getCourtCaseForMigration(courtCaseId = event.caseId)
        dpsApiService.refreshCaseIdentifiers(
          courtCaseId = mapping.dpsCourtCaseId,
          courtCaseLegacyData = CourtCaseLegacyData(
            nomisCourtCase.caseInfoNumbers.filter { it.type == DPS_CASE_REFERENCE }
              .map { it.toDpsCaseReference() },
          ),
        )
        telemetryClient.trackEvent(
          "case-identifiers-synchronisation-success",
          telemetry + ("dpsCourtCaseId" to mapping.dpsCourtCaseId) + ("offenderNo" to nomisCourtCase.offenderNo),
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
