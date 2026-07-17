package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.BadRequestException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class CourtSentencingRepairService(
  private val courtSentencingMigrationService: CourtSentencingMigrationService,
  private val courtSentencingSynchronisationService: CourtSentencingSynchronisationService,
  private val nomisApiService: CourtSentencingNomisApiService,
  private val dpsApiService: CourtSentencingDpsApiService,
  private val mappingApiService: CourtSentencingMappingApiService,
  private val telemetryClient: TelemetryClient,
) {
  suspend fun resynchronisePrisonerCourtCases(offenderNo: String) {
    courtSentencingMigrationService.synchronisePrisonerCases(
      offenderNo = offenderNo,
      deleteExisting = true,
      context = MigrationContext(
        type = MigrationType.COURT_SENTENCING,
        migrationId = LocalDateTime.now().withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        estimatedCount = 1,
        body = PrisonerId(offenderNo),
        properties = mutableMapOf(),

      ),
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
}
