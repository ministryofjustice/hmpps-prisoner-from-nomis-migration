package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse

@RestController
@RequestMapping("/migrate/bed-assignments", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Bed Assignment Migration Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW')")
class BedAssignmentMigrationResource(private val migrationService: BedAssignmentMigrationService) {
  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
    summary = "Starts a bed-assignment migration.",
    description = "Starts an asynchronous prisoner-by-prisoner migration of complete NOMIS histories. Requires role PRISONER_FROM_NOMIS__MIGRATION__RW.",
    responses = [
      ApiResponse(responseCode = "202", description = "Migration process started"),
      ApiResponse(responseCode = "400", description = "Invalid prisoner filter"),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "Migration already in progress",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun startBedAssignmentMigration(
    @RequestBody @Valid
    migrationFilter: PrisonerMigrationFilter,
  ) = migrationService.startMigration(migrationFilter)
}
