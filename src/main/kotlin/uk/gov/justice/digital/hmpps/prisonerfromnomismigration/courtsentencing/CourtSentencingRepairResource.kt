package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Court Sentencing Migration Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__UPDATE__RW')")
class CourtSentencingRepairResource(
  private val courtSentencingRepairService: CourtSentencingRepairService,
) {

  @PostMapping("/prisoners/{offenderNo}/court-sentencing/court-cases/repair")
  @Operation(
    summary = "We synchronises all prisoner cases from NOMIS to the DPS replacing the existing DPS cases",
    description = "Used cases in DPS have become out of sync with NOMIS and NOMIS is the source of truth, so emergency use only. This is equivalent to migrating cases again for a specific prisoner. Requires PRISONER_FROM_NOMIS__UPDATE__RW",
  )
  suspend fun prisonerCourtCasesRepair(
    @PathVariable
    offenderNo: String,
  ) {
    courtSentencingRepairService.resynchronisePrisonerCourtCases(
      offenderNo = offenderNo,
    )
  }
}
