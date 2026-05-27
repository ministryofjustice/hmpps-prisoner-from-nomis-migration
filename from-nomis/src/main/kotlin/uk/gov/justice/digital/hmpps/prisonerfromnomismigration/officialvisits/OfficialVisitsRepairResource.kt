package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse

@RestController
@Tag(name = "Official Visits Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__UPDATE__RW')")
class OfficialVisitsRepairResource(
  private val officialVisitsSynchronisationService: OfficialVisitsSynchronisationService,
) {

  @PostMapping("/prison/{prisonId}/prisoners/{offenderNo}/official-visits/{nomisVisitId}")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Create a visit in DPS from the visit in NOMIS",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW",
    responses = [
      ApiResponse(
        responseCode = "201",
        description = "Visit details created",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Visit mapping already exists",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint. Requires ROLE_NOMIS_PRISONER_API__SYNCHRONISATION__RW",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  suspend fun createOfficialVisitFromNomis(
    @PathVariable offenderNo: String,
    @PathVariable prisonId: String,
    @PathVariable nomisVisitId: Long,
  ) = officialVisitsSynchronisationService.createVisitFromNomis(
    offenderNo = offenderNo,
    prisonId = prisonId,
    nomisVisitId = nomisVisitId,
  )

  @PutMapping("/prison/{prisonId}/prisoners/{offenderNo}/official-visits/{nomisVisitId}")
  @Operation(
    summary = "Updates a visit in DPS from the visit in NOMIS, will create or update visitors as required",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit details updated",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint. Requires ROLE_NOMIS_PRISONER_API__SYNCHRONISATION__RW",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Visit mapping not found",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  suspend fun updateOfficialVisitFromNomis(
    @PathVariable offenderNo: String,
    @PathVariable prisonId: String,
    @PathVariable nomisVisitId: Long,
  ) = officialVisitsSynchronisationService.updateVisitFromNomis(
    offenderNo = offenderNo,
    prisonId = prisonId,
    nomisVisitId = nomisVisitId,
  )

  @PutMapping("/prisoners/{offenderNo}/official-visits/repair")
  @Operation(
    summary = "Recreates a visit in DPS from the visit in NOMIS for the specified prisoner",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Visit details recreated",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden to access this endpoint. Requires ROLE_NOMIS_PRISONER_API__SYNCHRONISATION__RW",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = ErrorResponse::class),
          ),
        ],
      ),
    ],
  )
  suspend fun repairVisitsFromNomis(
    @PathVariable offenderNo: String,
  ) = officialVisitsSynchronisationService.recreateVisitsFromNomis(
    offenderNo = offenderNo,
  )
}
