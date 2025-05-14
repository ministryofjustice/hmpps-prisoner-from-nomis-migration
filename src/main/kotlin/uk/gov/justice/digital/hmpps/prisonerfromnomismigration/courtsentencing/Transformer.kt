package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CaseReferenceLegacyData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.ChargeLegacyData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CourtAppearanceLegacyData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CourtCaseLegacyData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateCourtAppearance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateCourtCase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateFine
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreatePeriodLength
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateSentence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyUpdateCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtAppearance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateFine
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreatePeriodLength
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateSentence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationSentenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.NomisPeriodLengthId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.PeriodLengthLegacyData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.SentenceLegacyData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceTermResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

fun List<CourtCaseResponse>.findLinkedCaseOrNull(case: CourtCaseResponse): CourtCaseResponse? = this.firstOrNull { sourceCase -> case.id == sourceCase.combinedCaseId }
fun CourtCaseResponse.toMigrationDpsCourtCase(linkedSourceCase: CourtCaseResponse?) = MigrationCreateCourtCase(
  caseId = this.id,
  appearances = this.courtEvents.map { ca ->
    ca.toMigrationDpsCourtAppearance(this.sentences, linkedSourceCase, isSourceCase = this.combinedCaseId != null)
  },
  courtCaseLegacyData = CourtCaseLegacyData(
    caseReferences = this.caseInfoNumbers.map {
      CaseReferenceLegacyData(
        offenderCaseReference = it.reference,
        updatedDate = LocalDateTime.parse(it.createDateTime.toString()),
      )
    },
  ),
  active = this.caseStatus.code == "A",
  merged = if (this.combinedCaseId != null) {
    true
  } else if (linkedSourceCase != null) {
    false
  } else {
    null
  },
)

fun CourtCaseResponse.toLegacyDpsCourtCase() = LegacyCreateCourtCase(
  prisonerId = this.offenderNo,
  active = this.caseStatus.code == "A",
)

fun CourtEventResponse.toDpsCourtAppearance(
  dpsCaseId: String,
) = LegacyCreateCourtAppearance(
  courtCode = this.courtId,
  courtCaseUuid = dpsCaseId,
  appearanceDate = this.eventDateTime.toLocalDate(),
  appearanceTypeUuid = this.courtEventType.toDpsAppearanceTypeId(),
  legacyData =
  CourtAppearanceLegacyData(
    postedDate = LocalDate.now().toString(),
    outcomeDescription = this.outcomeReasonCode?.description,
    outcomeConvictionFlag = this.outcomeReasonCode?.conviction,
    outcomeDispositionCode = this.outcomeReasonCode?.dispositionCode,
    nomisOutcomeCode = this.outcomeReasonCode?.code,
    nextEventDateTime = this.nextEventDateTime,
    appearanceTime = this.eventDateTime.toLocalTime().toString(),
  ),
)

fun CourtEventResponse.toMigrationDpsCourtAppearance(
  sentences: List<SentenceResponse>,
  linkedSourceCase: CourtCaseResponse?,
  isSourceCase: Boolean,
) = MigrationCreateCourtAppearance(
  courtCode = this.courtId,
  appearanceDate = this.eventDateTime.toLocalDate(),
  appearanceTypeUuid = this.courtEventType.toDpsAppearanceTypeId(),
  eventId = this.id,
  legacyData =
  CourtAppearanceLegacyData(
    postedDate = LocalDate.now().toString(),
    outcomeDescription = this.outcomeReasonCode?.description,
    nomisOutcomeCode = this.outcomeReasonCode?.code,
    outcomeConvictionFlag = this.outcomeReasonCode?.conviction,
    outcomeDispositionCode = this.outcomeReasonCode?.dispositionCode,
    nextEventDateTime = this.nextEventDateTime,
    appearanceTime = this.eventDateTime.toLocalTime().toString(),
  ),

  /* supporting sentences with multiple charges
   */
  charges = this.courtEventCharges.map { charge ->
    // find sentence where sentence.offenderCharges contains charge
    val sentencesForAppearance = sentences.filter { sentence -> sentence.courtOrder?.eventId == this.id }

    val dpsSentence =
      sentencesForAppearance.find { sentence -> sentence.offenderCharges.any { it.id == charge.offenderCharge.id } }
        ?.toDpsMigrationSentence()
    charge.toDpsMigrationCharge(chargeId = charge.offenderCharge.id, dpsSentence = dpsSentence, linkedSourceCase = linkedSourceCase, isSourceCase = isSourceCase)
  },
)

fun OffenderChargeResponse.toDpsCharge(appearanceId: String) = LegacyCreateCharge(
  offenceCode = this.offence.offenceCode,
  offenceStartDate = this.offenceDate,
  legacyData =
  ChargeLegacyData(
    postedDate = LocalDate.now().toString(),
    outcomeDescription = this.resultCode1?.description,
    nomisOutcomeCode = this.resultCode1?.code,
    outcomeDispositionCode = this.resultCode1?.dispositionCode,
  ),
  offenceEndDate = this.offenceEndDate,
  appearanceLifetimeUuid = UUID.fromString(appearanceId),
)

fun CourtEventChargeResponse.toDpsCharge() = LegacyUpdateCharge(
  offenceStartDate = this.offenceDate,
  legacyData =
  ChargeLegacyData(
    postedDate = LocalDate.now().toString(),
    outcomeDescription = this.resultCode1?.description,
    nomisOutcomeCode = this.resultCode1?.code,
    outcomeDispositionCode = this.resultCode1?.dispositionCode,
  ),
  offenceEndDate = this.offenceEndDate,
)

fun CourtEventChargeResponse.toDpsMigrationCharge(
  chargeId: Long,
  dpsSentence: MigrationCreateSentence?,
  linkedSourceCase: CourtCaseResponse?,
  isSourceCase: Boolean,
): MigrationCreateCharge {
  val linkedCourtEvent =
    linkedSourceCase?.courtEvents?.firstOrNull { it.courtEventCharges.firstOrNull { it.offenderCharge.id == chargeId } != null }
  return MigrationCreateCharge(
    offenceCode = this.offenderCharge.offence.offenceCode,
    offenceStartDate = this.offenceDate,
    legacyData =
    ChargeLegacyData(
      postedDate = LocalDate.now().toString(),
      outcomeDescription = this.resultCode1?.description,
      nomisOutcomeCode = this.resultCode1?.code,
      outcomeDispositionCode = this.resultCode1?.dispositionCode,
    ),
    offenceEndDate = this.offenceEndDate,
    chargeNOMISId = chargeId,
    sentence = dpsSentence,
    merged = if (isSourceCase) {
      true
    } else if (linkedCourtEvent != null) {
      false
    } else {
      null
    },
    mergedFromCaseId = takeIf { linkedCourtEvent != null }?.let { linkedSourceCase?.id },
    mergedFromEventId = linkedCourtEvent?.id,
    // TODO - this will always be chargeId when linked so this feels
    // like a redundant field in DPS
    mergedChargeNOMISId = linkedCourtEvent?.courtEventCharges?.firstOrNull { it.offenderCharge.id == chargeId }?.offenderCharge?.id,
  )
}

fun SentenceResponse.toDpsSentence(sentenceChargeIds: List<String>, dpsConsecUuid: String?) = LegacyCreateSentence(
  chargeUuids = sentenceChargeIds.map { UUID.fromString(it) },
  active = this.status == "A",
  legacyData = this.toSentenceLegacyData(),
  // TODO confirm what this is used for
  chargeNumber = this.lineSequence?.toString(),
  fine = this.fineAmount?.let { LegacyCreateFine(fineAmount = it) },
  consecutiveToLifetimeUuid = dpsConsecUuid?.let { UUID.fromString(it) },
  returnToCustodyDate = this.recallCustodyDate?.returnToCustodyDate,
)

fun SentenceResponse.toDpsMigrationSentence() = MigrationCreateSentence(
  // TODO around 10% of nomis sentences have > 1 charge and 27 have no charges, so we need to handle this.
  active = this.status == "A",
  legacyData = this.toSentenceLegacyData(),
  periodLengths = this.sentenceTerms.map { it.toPeriodMigrationData(this) },
  chargeNumber = this.lineSequence?.toString(),
  fine = this.fineAmount?.let { MigrationCreateFine(fineAmount = it) },
  consecutiveToSentenceId = this.consecSequence?.let {
    MigrationSentenceId(
      offenderBookingId = this.bookingId,
      sequence = it,
    )
  },
  sentenceId = MigrationSentenceId(offenderBookingId = this.bookingId, sequence = this.sentenceSeq.toInt()),
  returnToCustodyDate = this.recallCustodyDate?.returnToCustodyDate,
)

fun SentenceResponse.toSentenceLegacyData() = SentenceLegacyData(
  sentenceCalcType = this.calculationType.code,
  sentenceCategory = this.category.code,
  sentenceTypeDesc = this.calculationType.description,
  postedDate = this.createdDateTime.toString(),
)

fun SentenceTermResponse.toPeriodLegacyData(dpsSentenceId: String) = LegacyCreatePeriodLength(
  periodYears = this.years,
  periodMonths = this.months,
  periodDays = this.days,
  periodWeeks = this.weeks,
  sentenceUuid = UUID.fromString(dpsSentenceId),
  legacyData = PeriodLengthLegacyData(
    lifeSentence = this.lifeSentenceFlag,
    sentenceTermCode = this.sentenceTermType?.code,
    sentenceTermDescription = this.sentenceTermType?.description,
  ),
)

fun SentenceTermResponse.toPeriodMigrationData(nomisSentence: SentenceResponse) = MigrationCreatePeriodLength(
  periodYears = this.years,
  periodMonths = this.months,
  periodDays = this.days,
  periodWeeks = this.weeks,
  periodLengthId = NomisPeriodLengthId(
    offenderBookingId = nomisSentence.bookingId,
    sentenceSequence = nomisSentence.sentenceSeq.toInt(),
    termSequence = this.termSequence.toInt(),
  ),
  legacyData = PeriodLengthLegacyData(
    lifeSentence = this.lifeSentenceFlag,
    sentenceTermCode = this.sentenceTermType?.code,
    sentenceTermDescription = this.sentenceTermType?.description,
  ),
)

fun CaseIdentifierResponse.toDpsCaseReference() = CaseReferenceLegacyData(offenderCaseReference = this.reference, updatedDate = this.createDateTime)

const val VIDEO_LINK_DPS_APPEARANCE_TYPE_UUID = "1da09b6e-55cb-4838-a157-ee6944f2094c"
const val COURT_APPEARANCE_DPS_APPEARANCE_TYPE_UUID = "63e8fce0-033c-46ad-9edf-391b802d547a"

private fun CodeDescription.toDpsAppearanceTypeId(): UUID = if (this.code.startsWith("VL")) {
  UUID.fromString(VIDEO_LINK_DPS_APPEARANCE_TYPE_UUID)
} else {
  UUID.fromString(COURT_APPEARANCE_DPS_APPEARANCE_TYPE_UUID)
}
