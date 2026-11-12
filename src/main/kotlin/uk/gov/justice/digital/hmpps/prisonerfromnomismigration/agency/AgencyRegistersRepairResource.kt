package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.model.DepartmentDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse

@RestController
@Tag(name = "Agency Repair Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__UPDATE__RW')")
@ApiResponses(
  value = [
    ApiResponse(responseCode = "200", description = "Successful Operation"),
    ApiResponse(
      responseCode = "401",
      description = "Unauthorized to access this endpoint",
      content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
    ),
    ApiResponse(
      responseCode = "403",
      description = "Missing required role. Requires the PRISONER_FROM_NOMIS__UPDATE__RW role",
      content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
    ),
  ],
)
class AgencyRegistersRepairResource(
  private val synchronisationService: AgencyRegistersSynchronisationService,
) {

  @PutMapping("/agency/{agencyId}/repair")
  @Operation(
    summary = "Repairs and synchronises an agency in DPS from the agency in NOMIS",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW",
  )
  suspend fun resynchroniseAgency(
    @PathVariable agencyId: String,
  ) = synchronisationService.resyncAgency(agencyId = agencyId)

  @PutMapping("/prison/{agencyId}/repair/phone-number")
  @Operation(
    summary = "Repairs and synchronises a prison phone number in DPS from the phone number in NOMIS",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW",
  )
  @ApiResponse(responseCode = "204", description = "Prison has no phone number in NOMIS so nothing to synchronise")
  suspend fun resynchronisePrisonPhoneNumber(
    @PathVariable agencyId: String,
  ): ResponseEntity<DepartmentDto> = synchronisationService.resyncPrisonPhoneNumber(agencyId = agencyId)
    ?.let { ResponseEntity.ok(it) }
    ?: ResponseEntity.noContent().build()
}
