package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import javax.validation.Valid

@RestController
@RequestMapping("/migrate", produces = [MediaType.APPLICATION_JSON_VALUE])
class VisitMigrationResource(private val visitsMigrationService: VisitsMigrationService) {
  @PreAuthorize("hasRole('ROLE_MIGRATE_VISITS')")
  @PostMapping("/visits")
  @ResponseStatus(value = ACCEPTED)
  @Operation(
    summary = "Starts a visit migration",
    description = "Starts an asynchronous migration process. This operation will return immediately and the migration will be performed asynchronously",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = VisitsMigrationFilter::class)
        )
      ]
    ),
    responses = [
      ApiResponse(
        responseCode = "202",
        description = "Migration process started",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to start migration",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun migrateVisits(@RequestBody @Valid migrationFilter: VisitsMigrationFilter) =
    visitsMigrationService.migrateVisits(migrationFilter)
}
