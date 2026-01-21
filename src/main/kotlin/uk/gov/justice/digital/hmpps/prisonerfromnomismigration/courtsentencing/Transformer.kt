package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.BookingCreateCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.BookingCreateCourtAppearance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.BookingCreateCourtCase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.BookingCreateFine
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.BookingCreatePeriodLength
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.BookingCreateSentence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.BookingSentenceId
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MergeCreateCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MergeCreateCourtAppearance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MergeCreateCourtCase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MergeCreateFine
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MergeCreatePeriodLength
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MergeCreateSentence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MergeSentenceId
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

fun CourtCaseResponse.toMigrationDpsCourtCase() = MigrationCreateCourtCase(
  caseId = this.id,
  appearances = this.courtEvents.map {
    it.toMigrationDpsCourtAppearance(this.sentences)
  },
  courtCaseLegacyData = CourtCaseLegacyData(
    caseReferences = this.caseInfoNumbers.map {
      CaseReferenceLegacyData(
        offenderCaseReference = it.reference,
        updatedDate = LocalDateTime.parse(it.createDateTime.toString()),
      )
    },
    bookingId = this.bookingId,
  ),
  active = this.caseStatus.code == "A",
  merged = if (this.combinedCaseId != null) {
    true
  } else if (this.sourceCombinedCaseIds.isNotEmpty()) {
    false
  } else {
    null
  },
)
fun CourtCaseResponse.toBookingCloneDpsCourtCase() = BookingCreateCourtCase(
  caseId = this.id,
  appearances = this.courtEvents.map {
    it.toBookingCloneDpsCourtAppearance(this.sentences)
  },
  courtCaseLegacyData = CourtCaseLegacyData(
    caseReferences = this.caseInfoNumbers.map {
      CaseReferenceLegacyData(
        offenderCaseReference = it.reference,
        updatedDate = LocalDateTime.parse(it.createDateTime.toString()),
      )
    },
    bookingId = this.bookingId,
  ),
  active = this.caseStatus.code == "A",
  merged = if (this.combinedCaseId != null) {
    true
  } else if (this.sourceCombinedCaseIds.isNotEmpty()) {
    false
  } else {
    null
  },
)
fun CourtCaseResponse.toDpsCourtCasePostMerge() = MergeCreateCourtCase(
  caseId = this.id,
  appearances = this.courtEvents.map {
    it.toMergeDpsCourtAppearance(this.sentences)
  },
  courtCaseLegacyData = CourtCaseLegacyData(
    caseReferences = this.caseInfoNumbers.map {
      CaseReferenceLegacyData(
        offenderCaseReference = it.reference,
        updatedDate = LocalDateTime.parse(it.createDateTime.toString()),
      )
    },
    bookingId = this.bookingId,
  ),
  active = this.caseStatus.code == "A",
  merged = if (this.combinedCaseId != null) {
    true
  } else if (this.sourceCombinedCaseIds.isNotEmpty()) {
    false
  } else {
    null
  },
)

fun CourtCaseResponse.toLegacyDpsCourtCase() = LegacyCreateCourtCase(
  prisonerId = this.offenderNo,
  active = this.caseStatus.code == "A",
  bookingId = this.bookingId,
  legacyData = CourtCaseLegacyData(
    caseReferences = this.caseInfoNumbers.map {
      CaseReferenceLegacyData(
        offenderCaseReference = it.reference,
        updatedDate = LocalDateTime.parse(it.createDateTime.toString()),
      )
    },
  ),
  performedByUser = this.modifiedByUsername ?: this.createdByUsername,
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
  performedByUser = this.modifiedByUsername ?: this.createdByUsername,
)

fun CourtEventResponse.toMigrationDpsCourtAppearance(
  sentences: List<SentenceResponse>,
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
    charge.toDpsMigrationCharge(chargeId = charge.offenderCharge.id, dpsSentence = dpsSentence)
  },
)
fun CourtEventResponse.toBookingCloneDpsCourtAppearance(
  sentences: List<SentenceResponse>,
) = BookingCreateCourtAppearance(
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
        ?.toDpsBookingCloneSentence()
    charge.toDpsBookingCloneCharge(chargeId = charge.offenderCharge.id, dpsSentence = dpsSentence)
  },
)
fun CourtEventResponse.toMergeDpsCourtAppearance(
  sentences: List<SentenceResponse>,
) = MergeCreateCourtAppearance(
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
        ?.toMergeSentence()
    charge.toMergeCharge(chargeId = charge.offenderCharge.id, dpsSentence = dpsSentence)
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
    outcomeConvictionFlag = this.resultCode1?.conviction,
    offenceDescription = this.offence.description,
  ),
  offenceEndDate = this.offenceEndDate,
  appearanceLifetimeUuid = UUID.fromString(appearanceId),
  performedByUser = this.modifiedByUsername ?: this.createdByUsername,
)

fun CourtEventChargeResponse.toDpsCharge(appearanceId: String) = LegacyCreateCharge(
  offenceCode = this.offenderCharge.offence.offenceCode,
  offenceStartDate = this.offenceDate,
  legacyData =
  ChargeLegacyData(
    postedDate = LocalDate.now().toString(),
    outcomeDescription = this.resultCode1?.description,
    nomisOutcomeCode = this.resultCode1?.code,
    outcomeDispositionCode = this.resultCode1?.dispositionCode,
    outcomeConvictionFlag = this.resultCode1?.conviction,
    offenceDescription = this.offenderCharge.offence.description,
  ),
  offenceEndDate = this.offenceEndDate,
  appearanceLifetimeUuid = UUID.fromString(appearanceId),
  performedByUser = this.modifiedByUsername ?: this.createdByUsername,
)

fun CourtEventChargeResponse.toDpsCharge() = LegacyUpdateCharge(
  offenceStartDate = this.offenceDate,
  legacyData =
  ChargeLegacyData(
    postedDate = LocalDate.now().toString(),
    outcomeDescription = this.resultCode1?.description,
    nomisOutcomeCode = this.resultCode1?.code,
    outcomeDispositionCode = this.resultCode1?.dispositionCode,
    outcomeConvictionFlag = this.resultCode1?.conviction,
    offenceDescription = this.offenderCharge.offence.description,
  ),
  offenceEndDate = this.offenceEndDate,
  performedByUser = this.modifiedByUsername ?: this.createdByUsername,
  // offenceCode has been added here - it cannot change as part of an court_event_charge update but race conditions make the latest value necessary
  offenceCode = this.offenderCharge.offence.offenceCode,
)

fun CourtEventChargeResponse.toDpsMigrationCharge(
  chargeId: Long,
  dpsSentence: MigrationCreateSentence?,
): MigrationCreateCharge = MigrationCreateCharge(
  offenceCode = this.offenderCharge.offence.offenceCode,
  offenceStartDate = this.offenceDate,
  legacyData =
  ChargeLegacyData(
    postedDate = LocalDate.now().toString(),
    outcomeDescription = this.resultCode1?.description,
    nomisOutcomeCode = this.resultCode1?.code,
    outcomeDispositionCode = this.resultCode1?.dispositionCode,
    outcomeConvictionFlag = this.resultCode1?.conviction,
    offenceDescription = this.offenderCharge.offence.description,
  ),
  offenceEndDate = this.offenceEndDate,
  chargeNOMISId = chargeId,
  sentence = dpsSentence,
  mergedFromCaseId = linkedCaseDetails?.caseId,
  mergedFromDate = linkedCaseDetails?.dateLinked,
)
fun CourtEventChargeResponse.toDpsBookingCloneCharge(
  chargeId: Long,
  dpsSentence: BookingCreateSentence?,
): BookingCreateCharge = BookingCreateCharge(
  offenceCode = this.offenderCharge.offence.offenceCode,
  offenceStartDate = this.offenceDate,
  legacyData =
  ChargeLegacyData(
    postedDate = LocalDate.now().toString(),
    outcomeDescription = this.resultCode1?.description,
    nomisOutcomeCode = this.resultCode1?.code,
    outcomeDispositionCode = this.resultCode1?.dispositionCode,
    outcomeConvictionFlag = this.resultCode1?.conviction,
    offenceDescription = this.offenderCharge.offence.description,
  ),
  offenceEndDate = this.offenceEndDate,
  chargeNOMISId = chargeId,
  sentence = dpsSentence,
  mergedFromCaseId = linkedCaseDetails?.caseId,
  mergedFromDate = linkedCaseDetails?.dateLinked,
)

fun CourtEventChargeResponse.toMergeCharge(
  chargeId: Long,
  dpsSentence: MergeCreateSentence?,
): MergeCreateCharge = MergeCreateCharge(
  offenceCode = this.offenderCharge.offence.offenceCode,
  offenceStartDate = this.offenceDate,
  legacyData =
  ChargeLegacyData(
    postedDate = LocalDate.now().toString(),
    outcomeDescription = this.resultCode1?.description,
    nomisOutcomeCode = this.resultCode1?.code,
    outcomeDispositionCode = this.resultCode1?.dispositionCode,
    outcomeConvictionFlag = this.resultCode1?.conviction,
    offenceDescription = this.offenderCharge.offence.description,
  ),
  offenceEndDate = this.offenceEndDate,
  chargeNOMISId = chargeId,
  sentence = dpsSentence,
  mergedFromCaseId = linkedCaseDetails?.caseId,
  mergedFromDate = linkedCaseDetails?.dateLinked,
)

fun SentenceResponse.toDpsSentence(sentenceChargeIds: List<String>, dpsAppearanceUuid: String, dpsConsecUuid: String?) = LegacyCreateSentence(
  chargeUuids = sentenceChargeIds.map { UUID.fromString(it) },
  active = this.status == "A",
  legacyData = this.toSentenceLegacyData(),
  fine = this.fineAmount?.let { LegacyCreateFine(fineAmount = it) },
  consecutiveToLifetimeUuid = dpsConsecUuid?.let { UUID.fromString(it) },
  returnToCustodyDate = this.recallCustodyDate?.returnToCustodyDate,
  appearanceUuid = UUID.fromString(dpsAppearanceUuid),
  performedByUser = this.modifiedByUsername ?: this.createdByUsername,
)

fun SentenceResponse.toDpsMigrationSentence() = MigrationCreateSentence(
  // TODO around 10% of nomis sentences have > 1 charge and 27 have no charges, so we need to handle this.
  active = this.status == "A",
  legacyData = this.toSentenceLegacyData(),
  periodLengths = this.sentenceTerms.map { it.toPeriodMigrationData(this) },
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
fun SentenceResponse.toDpsBookingCloneSentence() = BookingCreateSentence(
  active = this.status == "A",
  legacyData = this.toSentenceLegacyData(),
  periodLengths = this.sentenceTerms.map { it.toPeriodBookingCloneData(this) },
  fine = this.fineAmount?.let { BookingCreateFine(fineAmount = it) },
  consecutiveToSentenceId = this.consecSequence?.let {
    BookingSentenceId(
      offenderBookingId = this.bookingId,
      sequence = it,
    )
  },
  sentenceId = BookingSentenceId(offenderBookingId = this.bookingId, sequence = this.sentenceSeq.toInt()),
  returnToCustodyDate = this.recallCustodyDate?.returnToCustodyDate,
)
fun SentenceResponse.toMergeSentence() = MergeCreateSentence(
  // TODO around 10% of nomis sentences have > 1 charge and 27 have no charges, so we need to handle this.
  active = this.status == "A",
  legacyData = this.toSentenceLegacyData(),
  periodLengths = this.sentenceTerms.map { it.toMergePeriodData(this) },
  fine = this.fineAmount?.let { MergeCreateFine(fineAmount = it) },
  consecutiveToSentenceId = this.consecSequence?.let {
    MergeSentenceId(
      offenderBookingId = this.bookingId,
      sequence = it,
    )
  },
  sentenceId = MergeSentenceId(offenderBookingId = this.bookingId, sequence = this.sentenceSeq.toInt()),
  returnToCustodyDate = this.recallCustodyDate?.returnToCustodyDate,
)

fun SentenceResponse.toSentenceLegacyData() = SentenceLegacyData(
  sentenceCalcType = this.calculationType.code,
  sentenceCategory = this.category.code,
  sentenceTypeDesc = this.calculationType.description,
  postedDate = this.createdDateTime.toString(),
  nomisLineReference = this.lineSequence?.toString(),
  bookingId = this.bookingId,
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
  performedByUser = this.modifiedByUsername ?: this.createdByUsername,
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
fun SentenceTermResponse.toPeriodBookingCloneData(nomisSentence: SentenceResponse) = BookingCreatePeriodLength(
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
fun SentenceTermResponse.toMergePeriodData(nomisSentence: SentenceResponse) = MergeCreatePeriodLength(
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
