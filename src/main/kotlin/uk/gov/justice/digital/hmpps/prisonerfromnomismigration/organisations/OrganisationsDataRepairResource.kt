package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Organisation Date Repair Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__UPDATE__RW')")
class OrganisationsDataRepairResource(
  private val organisationsSynchronisationService: OrganisationsSynchronisationService,
) {

  @PostMapping("/organisation/{organisationId}/resynchronise")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Resynchronises organisation and addresses from NOMIS back to DPS",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW",
  )
  suspend fun repairOrganisation(
    @PathVariable organisationId: Long,
  ) {
    organisationsSynchronisationService.resynchronizeOrganisation(organisationId)
  }
}
