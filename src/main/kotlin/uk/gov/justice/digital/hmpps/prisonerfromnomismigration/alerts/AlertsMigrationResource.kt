package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext

@RestController
@RequestMapping("/migrate", produces = [MediaType.APPLICATION_JSON_VALUE])
class AlertsMigrationResource(val alertsMigrationService: AlertsMigrationService) {
  @PreAuthorize("hasRole('ROLE_MIGRATE_ALERTS')")
  @PostMapping("/alerts")
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  @Operation(
    summary = "Starts an alerts migration",
    description = "Starts an asynchronous migration process. This operation will return immediately and the migration will be performed asynchronously. Requires role <b>MIGRATE_ALERTS</b>",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = AlertsMigrationFilter::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "202",
        description = "Migration process started",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = MigrationContext::class))],
      ),
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
    ],
  )
  suspend fun migrateSentencing(
    @RequestBody @Valid
    migrationFilter: AlertsMigrationFilter,
  ) = alertsMigrationService.startMigration(migrationFilter)
}
