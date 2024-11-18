package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CaseReferenceLegacyData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.ChargeLegacyData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CourtAppearanceLegacyData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CourtCaseLegacyData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtAppearance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtCase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateCourtCase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CaseIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SentenceResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

fun CourtCaseResponse.toDpsCourtCase() = CreateCourtCase(
  prisonerId = this.offenderNo,
  appearances = this.courtEvents.map { ca ->
    ca.toDpsCourtAppearance(
      bookingId = this.bookingId,
      caseReference = this.primaryCaseInfoNumber,
    )
  },
  legacyData = CourtCaseLegacyData(
    caseReferences = this.caseInfoNumbers.map {
      CaseReferenceLegacyData(
        offenderCaseReference = it.reference,
        updatedDate = it.createDateTime,
      )
    },
  ),
)

fun CourtCaseResponse.toLegacyDpsCourtCase() = LegacyCreateCourtCase(
  prisonerId = this.offenderNo,
  active = true,
)

private const val WARRANT_TYPE_DEFAULT = "REMAND"
private const val OUTCOME_DEFAULT = "3034"

fun CourtEventResponse.toDpsCourtAppearance(
  bookingId: Long,
  dpsCaseId: String? = null,
  caseReference: String? = null,
) = CreateCourtAppearance(
  courtCode = this.courtId,
  // Only handling appearances associated with a case
  courtCaseUuid = dpsCaseId,
  // case references are not associated with an appearance on NOMIS, using latest (or possibly the version on OffenderCase) for all appearances
  courtCaseReference = caseReference,
  appearanceDate = LocalDateTime.parse(this.eventDateTime).toLocalDate(),
  warrantType = WARRANT_TYPE_DEFAULT,
  legacyData =
  CourtAppearanceLegacyData(
    eventId = this.id.toString(),
    caseId = this.caseId?.toString(),
    postedDate = LocalDate.now().toString(),
    outcomeDescription = this.outcomeReasonCode?.description,
    nomisOutcomeCode = this.outcomeReasonCode?.code,
  ),
  charges = this.courtEventCharges.map { charge -> charge.offenderCharge.toDpsCharge(bookingId = bookingId) },
)

fun OffenderChargeResponse.toDpsCharge(chargeId: String? = null, bookingId: Long) = CreateCharge(
  offenceCode = this.offence.offenceCode,
  // TODO determine if this is ever optional on NOMIS
  offenceStartDate = this.offenceDate!!,
  legacyData =
  ChargeLegacyData(
    offenderChargeId = this.id.toString(),
    bookingId = bookingId.toString(),
    postedDate = LocalDate.now().toString(),
    outcomeDescription = this.resultCode1?.description,
    nomisOutcomeCode = this.resultCode1?.code,
  ),
  offenceEndDate = this.offenceEndDate,
  chargeUuid = chargeId?.let { UUID.fromString(chargeId) },
)

fun SentenceResponse.toDpsSentence(offenderNo: String, sentenceChargeIds: List<String>) = CreateSentenceRequest(
  prisonerId = offenderNo,
  chargeUuids = sentenceChargeIds.map { UUID.fromString(it) },
)

// TODO confirm that DPS are no longer using ZoneTimeDate  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
fun CaseIdentifierResponse.toDpsCaseReference() =
  CaseReferenceLegacyData(offenderCaseReference = this.reference, updatedDate = this.createDateTime)
