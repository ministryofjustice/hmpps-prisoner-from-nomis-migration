package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtCase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtCaseResponse

fun CourtCaseResponse.toDPsCourtCase(offenderNo: String) = CreateCourtCase(
  prisonerId = offenderNo,
  appearances = emptyList(),
)
