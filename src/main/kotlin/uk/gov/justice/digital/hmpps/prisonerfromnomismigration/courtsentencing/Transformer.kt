package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtAppearance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtCase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SentenceResponse
import java.time.LocalDateTime
import java.util.UUID

fun CourtCaseResponse.toDpsCourtCase() = CreateCourtCase(
  prisonerId = this.offenderNo,
  appearances = emptyList(),
)

fun CourtCaseResponse.toDpsCourtCaseMigration() = CreateCourtCaseMigrationRequest(
  prisonerId = this.offenderNo,
  appearances = this.courtEvents.map { ca -> ca.toDpsCourtAppearance(offenderNo = offenderNo, caseReference = this.caseInfoNumber) },
  // TODO map to the list of case identifiers when returned from nomis
  otherCaseReferences = emptyList(),
)

private const val WARRANT_TYPE_DEFAULT = "REMAND"
private const val OUTCOME_DEFAULT = "3034"

fun CourtEventResponse.toDpsCourtAppearance(offenderNo: String, dpsCaseId: String? = null, caseReference: String? = null) = CreateCourtAppearance(
  // TODO determine what happens when no result in NOMIS (approx 10% of CAs associated with a case)
  outcome = this.outcomeReasonCode?.code ?: OUTCOME_DEFAULT,
  courtCode = this.courtId,
  // Only handling appearances associated with a case
  courtCaseUuid = dpsCaseId,
  // case references are not associated with an appearance on NOMIS, using latest (or possibly the version on OffenderCase) for all appearances
  courtCaseReference = caseReference,
  appearanceDate = LocalDateTime.parse(this.eventDateTime).toLocalDate(),
  warrantType = WARRANT_TYPE_DEFAULT,
  charges = this.courtEventCharges.map { charge -> charge.offenderCharge.toDpsCharge() },
)

fun OffenderChargeResponse.toDpsCharge(chargeId: String? = null) = CreateCharge(
  offenceCode = this.offence.offenceCode,
  // TODO determine if this is ever optional on NOMIS
  offenceStartDate = this.offenceDate!!,
  // TODO can be persisted without a result code in NOMIS
  outcome = this.resultCode1?.code ?: "PLACEHOLDER",
  offenceEndDate = this.offenceEndDate,
  chargeUuid = chargeId?.let { UUID.fromString(chargeId) },
)

fun SentenceResponse.toDpsSentence(offenderNo: String, sentenceChargeIds: List<String>) = CreateSentenceRequest(
  prisonerId = offenderNo,
  chargeUuids = sentenceChargeIds.map { UUID.fromString(it) },
)
