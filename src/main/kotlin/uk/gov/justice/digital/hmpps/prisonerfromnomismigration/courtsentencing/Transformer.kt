package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CaseReferenceLegacyData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.ChargeLegacyData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CourtAppearanceLegacyData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CourtCaseLegacyData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateCourtAppearance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCreateCourtCase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtAppearance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CaseIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtEventChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SentenceResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

fun CourtCaseResponse.toMigrationDpsCourtCase() = MigrationCreateCourtCase(
  prisonerId = this.offenderNo,
  appearances = this.courtEvents.map { ca ->
    ca.toMigrationDpsCourtAppearance()
  },
  courtCaseLegacyData = CourtCaseLegacyData(
    caseReferences = this.caseInfoNumbers.map {
      CaseReferenceLegacyData(
        offenderCaseReference = it.reference,
        updatedDate = LocalDateTime.parse(it.createDateTime),
      )
    },
  ),
  active = this.caseStatus.code == "A",
)

fun CourtCaseResponse.toLegacyDpsCourtCase() = LegacyCreateCourtCase(
  prisonerId = this.offenderNo,
  active = true,
)

private const val WARRANT_TYPE_DEFAULT = "REMAND"
private const val OUTCOME_DEFAULT = "3034"

fun CourtEventResponse.toDpsCourtAppearance(
  caseReference: String? = null,
  dpsCaseId: String,
) = LegacyCreateCourtAppearance(
  courtCode = this.courtId,
  courtCaseUuid = dpsCaseId,
  appearanceDate = LocalDateTime.parse(this.eventDateTime).toLocalDate(),
  legacyData =
  CourtAppearanceLegacyData(
    eventId = this.id.toString(),
    caseId = this.caseId?.toString(),
    postedDate = LocalDate.now().toString(),
    outcomeDescription = this.outcomeReasonCode?.description,
    nomisOutcomeCode = this.outcomeReasonCode?.code,
    nextEventDateTime = this.nextEventDateTime?.let { LocalDateTime.parse(this.nextEventDateTime) },
  ),
)

fun CourtEventResponse.toMigrationDpsCourtAppearance() = MigrationCreateCourtAppearance(
  courtCode = this.courtId,
  appearanceDate = LocalDateTime.parse(this.eventDateTime).toLocalDate(),
  legacyData =
  CourtAppearanceLegacyData(
    eventId = this.id.toString(),
    caseId = this.caseId?.toString(),
    postedDate = LocalDate.now().toString(),
    outcomeDescription = this.outcomeReasonCode?.description,
    nomisOutcomeCode = this.outcomeReasonCode?.code,
    nextEventDateTime = this.nextEventDateTime?.let { LocalDateTime.parse(this.nextEventDateTime) },
  ),
  charges = this.courtEventCharges.map { charge -> charge.offenderCharge.toDpsMigrationCharge(chargeId = charge.offenderCharge.id) },
)

fun OffenderChargeResponse.toDpsCharge(appearanceId: String) = LegacyCreateCharge(
  offenceCode = this.offence.offenceCode,
  offenceStartDate = this.offenceDate!!,
  legacyData =
  ChargeLegacyData(
    postedDate = LocalDate.now().toString(),
    outcomeDescription = this.resultCode1?.description,
    nomisOutcomeCode = this.resultCode1?.code,
  ),
  offenceEndDate = this.offenceEndDate,
  active = this.chargeStatus?.code == "A",
  appearanceLifetimeUuid = UUID.fromString(appearanceId),
)

fun CourtEventChargeResponse.toDpsCharge(appearanceId: String) = LegacyCreateCharge(
  offenceCode = this.offenderCharge.offence.offenceCode,
  offenceStartDate = this.offenceDate!!,
  legacyData =
  ChargeLegacyData(
    postedDate = LocalDate.now().toString(),
    outcomeDescription = this.resultCode1?.description,
    nomisOutcomeCode = this.resultCode1?.code,
  ),
  offenceEndDate = this.offenceEndDate,
  active = this.offenderCharge.chargeStatus?.code == "A",
  appearanceLifetimeUuid = UUID.fromString(appearanceId),
)

fun OffenderChargeResponse.toDpsMigrationCharge(chargeId: Long) = MigrationCreateCharge(
  offenceCode = this.offence.offenceCode,
  offenceStartDate = this.offenceDate!!,
  legacyData =
  ChargeLegacyData(
    postedDate = LocalDate.now().toString(),
    outcomeDescription = this.resultCode1?.description,
    nomisOutcomeCode = this.resultCode1?.code,
  ),
  offenceEndDate = this.offenceEndDate,
  chargeNOMISId = chargeId.toString(),
  active = true,
)

fun SentenceResponse.toDpsSentence(offenderNo: String, sentenceChargeIds: List<String>) = CreateSentenceRequest(
  prisonerId = offenderNo,
  chargeUuids = sentenceChargeIds.map { UUID.fromString(it) },
)

// TODO confirm that DPS are no longer using ZoneTimeDate  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
fun CaseIdentifierResponse.toDpsCaseReference() =
  CaseReferenceLegacyData(offenderCaseReference = this.reference, updatedDate = LocalDateTime.parse(this.createDateTime))
