package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtAppearance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtCase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtEventResponse
import java.time.LocalDateTime

fun CourtCaseResponse.toDpsCourtCase(offenderNo: String) = CreateCourtCase(
  prisonerId = offenderNo,
  appearances = emptyList(),
)

// TODO transformations nomis-dps
fun CourtEventResponse.toDpsCourtAppearance(offenderNo: String, dpsCaseId: String?) = CreateCourtAppearance(
  outcome = "outcome",
  courtCode = this.courtId,
  // TODO court_events caseId is optional in nomis  - determine how to handle this
  courtCaseUuid = dpsCaseId!!,
  courtCaseReference = "caseRef",
  appearanceDate = LocalDateTime.parse(this.eventDateTime).toLocalDate(),
  warrantType = "warrantType",
  charges = emptyList(),
)
