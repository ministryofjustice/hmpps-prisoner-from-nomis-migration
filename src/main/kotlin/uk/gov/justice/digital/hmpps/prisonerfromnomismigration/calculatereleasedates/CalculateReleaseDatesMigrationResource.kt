package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

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
@RequestMapping("/migrate/calculate-release-dates", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Calculate Release Dates Migration Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW')")
class CalculateReleaseDatesMigrationResource(
  private val migrationService: CalculateReleaseDatesMigrationService,
) {
  @PostMapping
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  @Operation(
    summary = "Starts a calculate release dates migration",
    description = "Starts an asynchronous migration process to migrate sentence calculations for all prisoners, or for a single prisoner if one is supplied. Requires role <b>PRISONER_FROM_NOMIS__MIGRATION__RW</b>",
    responses = [
      ApiResponse(responseCode = "202", description = "Calculate release dates migration process started"),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to start migration",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "Migration already in progress",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun migrateCalculateReleaseDates(
    @RequestBody @Valid migrationFilter: CalculateReleaseDatesMigrationFilter,
  ) = migrationService.startMigration(migrationFilter)
}
