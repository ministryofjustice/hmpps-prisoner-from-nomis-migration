package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.BadRequestException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCases
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCasesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreatePeriodLengthResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateSentenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.DuplicateErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseBatchMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentenceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentenceTermMappingDto
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class CourtSentencingRepairService(
  private val courtSentencingSynchronisationService: CourtSentencingSynchronisationService,
  private val nomisApiService: CourtSentencingNomisApiService,
  private val dpsApiService: CourtSentencingDpsApiService,
  private val mappingApiService: CourtSentencingMappingApiService,
  private val telemetryClient: TelemetryClient,
) {
  suspend fun resynchronisePrisonerCourtCases(offenderNo: String) {
    synchronisePrisonerCases(
      offenderNo = offenderNo,
      migrationId = LocalDateTime.now().withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
    ) {
      telemetryClient.trackEvent(
        "court-sentencing-prisoner-cases-repaired",
        mapOf(
          "offenderNo" to offenderNo,
        ),
        null,
      )
    }
  }

  suspend fun resynchronisePrisonerCourtCaseStatus(offenderNo: String, bookingId: Long, caseId: Long) {
    courtSentencingSynchronisationService.nomisCourtCaseUpdated(
      CourtCaseEvent(
        caseId = caseId,
        offenderIdDisplay = offenderNo,
        bookingId = bookingId,
        auditModuleName = "NOMIS",
      ),
    )

    telemetryClient.trackEvent(
      "court-sentencing-prisoner-case-status-repaired",
      mapOf(
        "offenderNo" to offenderNo,
        "nomisBookingId" to bookingId.toString(),
        "nomisCourtCaseId" to caseId.toString(),
      ),
      null,
    )
  }

  suspend fun resynchronisePrisonerSentenceTermInsert(
    offenderNo: String,
    bookingId: Long,
    sentenceSeq: Int,
    termSeq: Int,
  ) {
    courtSentencingSynchronisationService.nomisSentenceTermInserted(
      OffenderSentenceTermEvent(
        sentenceSeq = sentenceSeq,
        offenderIdDisplay = offenderNo,
        bookingId = bookingId,
        termSequence = termSeq,
        auditModuleName = "NOMIS",
      ),
    )

    telemetryClient.trackEvent(
      "court-sentencing-prisoner-sentence-term-repaired",
      mapOf(
        "offenderNo" to offenderNo,
        "nomisBookingId" to bookingId.toString(),
        "nomisSentenceSequence" to sentenceSeq.toString(),
        "nomisTermSequence" to termSeq.toString(),
      ),
      null,
    )
  }

  suspend fun resynchronisePrisonerSentenceInsert(
    offenderNo: String,
    bookingId: Long,
    sentenceSeq: Int,
    caseId: Long,
    sentenceLevel: String,
    sentenceCategory: String,
  ) {
    courtSentencingSynchronisationService.nomisSentenceInserted(
      OffenderSentenceEvent(
        sentenceSeq = sentenceSeq,
        offenderIdDisplay = offenderNo,
        bookingId = bookingId,
        sentenceLevel = sentenceLevel,
        sentenceCategory = sentenceCategory,
        caseId = caseId,
        auditModuleName = "NOMIS",
      ),
    )

    telemetryClient.trackEvent(
      "court-sentencing-prisoner-sentence-repaired",
      mapOf(
        "offenderNo" to offenderNo,
        "nomisBookingId" to bookingId.toString(),
        "nomisSentenceSequence" to sentenceSeq.toString(),
        "nomisSentenceLevel" to sentenceLevel,
        "nomisSentenceCategory" to sentenceCategory,
        "caseId" to caseId.toString(),
      ),
      null,
    )
  }

  suspend fun resynchroniseCourtEventChargeInsert(
    offenderNo: String,
    bookingId: Long,
    eventId: Long,
    chargeId: Long,
  ) {
    courtSentencingSynchronisationService.nomisCourtChargeInserted(
      CourtEventChargeEvent(
        offenderIdDisplay = offenderNo,
        bookingId = bookingId,
        eventId = eventId,
        chargeId = chargeId,
        auditModuleName = "NOMIS",
      ),
    )

    telemetryClient.trackEvent(
      "court-charge-synchronisation-created-repaired",
      mapOf(
        "offenderNo" to offenderNo,
        "nomisBookingId" to bookingId.toString(),
        "nomisOffenderChargeId" to chargeId.toString(),
        "nomisCourtAppearanceId" to eventId.toString(),
      ),
      null,
    )
  }

  suspend fun resynchronisePrisonerSentenceUpdated(
    offenderNo: String,
    bookingId: Long,
    caseId: Long,
    sentenceSeq: Int,
  ) {
    courtSentencingSynchronisationService.nomisSentenceUpdated(
      OffenderSentenceEvent(
        sentenceSeq = sentenceSeq,
        offenderIdDisplay = offenderNo,
        bookingId = bookingId,
        sentenceLevel = "IND",
        sentenceCategory = "220",
        caseId = caseId,
        auditModuleName = "NOMIS",
      ),
    )

    telemetryClient.trackEvent(
      "court-sentencing-prisoner-sentence-update-repaired",
      mapOf(
        "offenderNo" to offenderNo,
        "nomisBookingId" to bookingId.toString(),
        "nomisCaseId" to caseId.toString(),
        "nomisSentenceSequence" to sentenceSeq.toString(),
      ),
      null,
    )
  }

  suspend fun resynchroniseAppearanceUpdated(
    offenderNo: String,
    bookingId: Long,
    caseId: Long,
    eventId: Long,
  ) {
    courtSentencingSynchronisationService.nomisCourtAppearanceUpdated(
      CourtAppearanceEvent(
        eventId = eventId,
        offenderIdDisplay = offenderNo,
        bookingId = bookingId,
        caseId = caseId,
        auditModuleName = "NOMIS",
      ),
    )

    telemetryClient.trackEvent(
      "court-sentencing-appearance-update-repaired",
      mapOf(
        "offenderNo" to offenderNo,
        "nomisBookingId" to bookingId.toString(),
        "nomisCaseId" to caseId.toString(),
        "nomisCourtAppearanceId" to eventId.toString(),
      ),
      null,
    )
  }
  suspend fun resynchroniseAppearanceCreated(
    offenderNo: String,
    bookingId: Long,
    caseId: Long,
    eventId: Long,
  ) {
    courtSentencingSynchronisationService.nomisCourtAppearanceInserted(
      CourtAppearanceEvent(
        eventId = eventId,
        offenderIdDisplay = offenderNo,
        bookingId = bookingId,
        caseId = caseId,
        auditModuleName = "NOMIS",
      ),
    )

    telemetryClient.trackEvent(
      "court-sentencing-appearance-created-repaired",
      mapOf(
        "offenderNo" to offenderNo,
        "nomisBookingId" to bookingId.toString(),
        "nomisCaseId" to caseId.toString(),
        "nomisCourtAppearanceId" to eventId.toString(),
      ),
      null,
    )
  }

  suspend fun removedUnMappedCourtAppearancesFromCase(offenderNo: String, caseId: Long) {
    val nomisCourtCase = nomisApiService.getCourtCase(offenderNo = offenderNo, courtCaseId = caseId)
    val mapping = mappingApiService.getCourtCaseByNomisId(caseId)
    val dpsCourtCase = dpsApiService.getCourtCase(mapping.dpsCourtCaseId)
    val numberOfAdditionalAppearances = dpsCourtCase.appearances.size - nomisCourtCase.courtEvents.size
    val dpsCourtAppearanceIdsDeleted = mutableListOf<String>()

    // if we have additional appearances in DPS start pruning
    if (nomisCourtCase.courtEvents.size < dpsCourtCase.appearances.size) {
      val nomisIds = nomisCourtCase.courtEvents.map { it.id }
      val dpsIds = dpsCourtCase.appearances.map { it.appearanceUuid.toString() }
      val dpsIdsToKeep = nomisIds.map { nomisAppearanceId ->
        mappingApiService.getCourtAppearanceByNomisId(nomisAppearanceId).dpsCourtAppearanceId
      }.toSet()

      val dpsIdsToRemove = dpsIds - dpsIdsToKeep
      if (dpsIdsToRemove.size != numberOfAdditionalAppearances) {
        throw BadRequestException("Number of additional appearances in DPS does not match expected count. Expected: $numberOfAdditionalAppearances, Actual: ${dpsIdsToRemove.size}")
      }

      dpsIdsToRemove.forEach {
        dpsApiService.deleteCourtAppearance(it)
        dpsCourtAppearanceIdsDeleted.add(it)
      }
    }

    telemetryClient.trackEvent(
      "court-sentencing-prisoner-court-appearances-pruned",
      mapOf(
        "offenderNo" to offenderNo,
        "nomisCaseId" to caseId.toString(),
        "numberOfNomisCourtAppearances" to nomisCourtCase.courtEvents.size.toString(),
        "numberOfDpsCourtAppearances" to dpsCourtCase.appearances.size.toString(),
        "numberOfAdditionalAppearances" to numberOfAdditionalAppearances.toString(),
        "dpsCourtAppearanceIdsDeleted" to dpsCourtAppearanceIdsDeleted.joinToString(","),
      ),
      null,
    )
  }

  suspend fun repairCourtAppearanceCharges(offenderNo: String, caseId: Long, eventId: Long) {
    val telemetry = mutableMapOf(
      "nomisCourtAppearanceId" to eventId.toString(),
      "offenderNo" to offenderNo,
      "nomisCaseId" to caseId.toString(),
    )

    val nomisCourtAppearance = nomisApiService.getCourtAppearance(offenderNo = offenderNo, courtAppearanceId = eventId)

    val courtAppearanceMapping = mappingApiService.getCourtAppearanceOrNullByNomisId(eventId)
      ?: throw uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.ParentEntityNotFoundRetry("Court appearance mapping not found for event $eventId")
    telemetry["dpsCourtAppearanceId"] = courtAppearanceMapping.dpsCourtAppearanceId

    val dpsAppearance = dpsApiService.getCourtAppearance(courtAppearanceMapping.dpsCourtAppearanceId)

    val dpsChargeIdsPresent = dpsAppearance.charges.map { it.lifetimeUuid.toString() }.toMutableSet()

    val nomisChargeIdsPresent = nomisCourtAppearance.courtEventCharges.map { it.offenderCharge.id }.toSet()
    val expectedDpsChargeIds = nomisCourtAppearance.courtEventCharges.mapNotNull { courtEventCharge ->
      mappingApiService.getOffenderChargeOrNullByNomisId(courtEventCharge.offenderCharge.id)?.dpsCourtChargeId
    }.toSet()

    // Remove any DPS charges on the appearance that are unmapped
    val dpsChargeIdsToRemove = dpsChargeIdsPresent - expectedDpsChargeIds
    val removed = mutableListOf<String>()
    dpsChargeIdsToRemove.forEach { chargeId ->
      dpsApiService.removeCourtChargeAssociation(
        courtAppearanceId = courtAppearanceMapping.dpsCourtAppearanceId,
        chargeId = chargeId,
      )
      removed.add(chargeId)
    }

    val addedAssociations = mutableListOf<String>()

    // Ensure all NOMIS charges are present and associated
    nomisCourtAppearance.courtEventCharges.forEach { courtEventCharge ->
      val nomisChargeId = courtEventCharge.offenderCharge.id
      val existingMapping = mappingApiService.getOffenderChargeOrNullByNomisId(nomisChargeId)

      if (existingMapping != null) {
        // If mapped but not associated to this appearance, associate it
        if (!dpsChargeIdsPresent.contains(existingMapping.dpsCourtChargeId)) {
          dpsApiService.associateExistingCourtCharge(
            courtAppearanceId = courtAppearanceMapping.dpsCourtAppearanceId,
            chargeId = existingMapping.dpsCourtChargeId,
            charge = courtEventCharge.toDpsCharge(),
          )
          addedAssociations.add(existingMapping.dpsCourtChargeId)
        }
      } else {
        // map the nomis charge and create in dps
        courtSentencingSynchronisationService.nomisCourtChargeInserted(
          eventId = eventId,
          chargeId = nomisChargeId,
          offenderNo = offenderNo,
        ).also {
          mappingApiService.getOffenderChargeOrNullByNomisId(nomisChargeId)?.dpsCourtChargeId?.let { dpsId ->
            addedAssociations.add(dpsId)
          }
        }
      }
    }

    telemetry["nomisCharges"] = nomisChargeIdsPresent.joinToString(",")
    telemetry["dpsChargesRemoved"] = removed.joinToString(",")
    telemetry["dpsChargeAssociationsAdded"] = addedAssociations.joinToString(",")

    telemetryClient.trackEvent(
      "court-sentencing-prisoner-court-appearance-charges-repaired",
      telemetry,
      null,
    )
  }

  suspend fun repairCourtCaseInfoNumbersByCaseId(offenderNo: String, caseId: Long) {
    courtSentencingSynchronisationService.nomisCaseIdentifiersUpdated(
      eventName = "OFFENDER_CASE_IDENTIFIERS-UPDATED",
      event = CaseIdentifiersEvent(
        caseId = caseId,
        // all parameters used only for logging
        identifierType = "repair",
        identifierNo = "repair",
        auditModuleName = "repair",
      ),
    )
    telemetryClient.trackEvent(
      "court-sentencing-prisoner-court-case-info-numbers-updated",
      mapOf(
        "offenderNo" to offenderNo,
        "nomisCaseId" to caseId.toString(),
      ),
      null,
    )
  }

  suspend fun synchronisePrisonerCases(offenderNo: String, migrationId: String, onSuccess: () -> Unit) {
    val nomisCourtCases = nomisApiService.getCourtCasesForMigration(offenderNo = offenderNo)
    val dpsCases = nomisCourtCases.map { it.toMigrationDpsCourtCase() }
    dpsApiService.createCourtCaseMigration(
      MigrationCreateCourtCases(
        prisonerId = offenderNo,
        courtCases = dpsCases,
      ),
      deleteExisting = true,
    )
      .also { dpsCourtCaseCreateResponse ->
        createMigrationMapping(
          offenderNo = offenderNo,
          dpsCourtCasesCreateResponse = dpsCourtCaseCreateResponse,
          migrationId = migrationId,
        )
        onSuccess()
      }
  }

  private suspend fun createMigrationMapping(
    offenderNo: String,
    dpsCourtCasesCreateResponse: MigrationCreateCourtCasesResponse,
    migrationId: String,
  ) {
    val mapping = CourtCaseBatchMappingDto(
      courtCases = buildCourtCaseMapping(dpsCourtCasesCreateResponse.courtCases),
      courtCharges = buildCourtChargeMapping(dpsCourtCasesCreateResponse.charges),
      courtAppearances = buildCourtAppearanceMapping(dpsCourtCasesCreateResponse.appearances),
      sentences = buildSentenceMapping(dpsCourtCasesCreateResponse.sentences),
      sentenceTerms = buildSentenceTermMapping(dpsCourtCasesCreateResponse.sentenceTerms),
      label = migrationId,
      mappingType = CourtCaseBatchMappingDto.MappingType.MIGRATED,
    )

    try {
      mappingApiService.createMapping(
        offenderNo = offenderNo,
        mapping,
        object : ParameterizedTypeReference<DuplicateErrorResponse<CourtCaseBatchMappingDto>>() {},
      ).also {
        if (it.isError) {
          telemetryClient.trackEvent(
            "nomis-migration-court-sentencing-duplicate",
            mapOf<String, String>(
              "migrationId" to migrationId,
              "offenderId" to offenderNo,
            ),
            null,
          )
        }
      }
    } catch (e: Exception) {
      log.error("Failed to create mapping for Offender No: $offenderNo", e)
      throw e
    }
  }

  // dependent on court appearance order back from dps to match nomis
  private fun buildCourtAppearanceMapping(responseMappings: List<MigrationCreateCourtAppearanceResponse>): List<CourtAppearanceMappingDto> = responseMappings.map {
    CourtAppearanceMappingDto(
      nomisCourtAppearanceId = it.eventId,
      dpsCourtAppearanceId = it.appearanceUuid.toString(),
      mappingType = CourtAppearanceMappingDto.MappingType.MIGRATED,
    )
  }

  private fun buildCourtChargeMapping(responseMappings: List<MigrationCreateChargeResponse>): List<CourtChargeMappingDto> = responseMappings.map {
    CourtChargeMappingDto(
      nomisCourtChargeId = it.chargeNOMISId,
      dpsCourtChargeId = it.chargeUuid.toString(),
      mappingType = CourtChargeMappingDto.MappingType.MIGRATED,
    )
  }

  private fun buildSentenceMapping(responseMappings: List<MigrationCreateSentenceResponse>): List<SentenceMappingDto> = responseMappings.map {
    SentenceMappingDto(
      nomisSentenceSequence = it.sentenceNOMISId.sequence,
      nomisBookingId = it.sentenceNOMISId.offenderBookingId,
      dpsSentenceId = it.sentenceUuid.toString(),
      mappingType = SentenceMappingDto.MappingType.MIGRATED,
    )
  }

  private fun buildSentenceTermMapping(responseMappings: List<MigrationCreatePeriodLengthResponse>): List<SentenceTermMappingDto> = responseMappings.map {
    SentenceTermMappingDto(
      nomisSentenceSequence = it.sentenceTermNOMISId.sentenceSequence,
      nomisBookingId = it.sentenceTermNOMISId.offenderBookingId,
      dpsTermId = it.periodLengthUuid.toString(),
      nomisTermSequence = it.sentenceTermNOMISId.termSequence,
      mappingType = SentenceTermMappingDto.MappingType.MIGRATED,
    )
  }

  private fun buildCourtCaseMapping(responseMappings: List<MigrationCreateCourtCaseResponse>): List<CourtCaseMappingDto> = responseMappings.map {
    CourtCaseMappingDto(
      nomisCourtCaseId = it.caseId,
      dpsCourtCaseId = it.courtCaseUuid,
      mappingType = CourtCaseMappingDto.MappingType.MIGRATED,
    )
  }
}

private val log: Logger = LoggerFactory.getLogger(CourtSentencingRepairService::class.java)
