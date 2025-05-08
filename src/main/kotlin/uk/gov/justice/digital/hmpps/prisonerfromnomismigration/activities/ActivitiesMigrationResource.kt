package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse

@RestController
@RequestMapping("/migrate/activities", produces = [MediaType.APPLICATION_JSON_VALUE])
class ActivitiesMigrationResource(
  private val activitiesMigrationService: ActivitiesMigrationService,
) {
  @PreAuthorize("hasRole('ROLE_MIGRATE_ACTIVITIES')")
  @PostMapping
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  @Operation(
    summary = "Starts an activities migration",
    description = "Starts an asynchronous migration process. This operation will return immediately and the migration will be performed asynchronously. Requires role <b>MIGRATE_ACTIVITIES</b>",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = ActivitiesMigrationFilter::class),
        ),
      ],
    ),
    responses = [
      ApiResponse(
        responseCode = "202",
        description = "Migration process started",
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
      ApiResponse(
        responseCode = "409",
        description = "Migration already in progress",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun migrateActivities(
    @RequestBody @Valid
    migrationFilter: ActivitiesMigrationFilter,
  ) = activitiesMigrationService.startMigration(migrationFilter)

  @PreAuthorize("hasRole('ROLE_MIGRATE_ACTIVITIES')")
  @PutMapping("/{migrationId}/end")
  @Operation(
    summary = "End all activities and allocations for a migration",
    description = "Get all NOMIS activities migrated on a migrationId and ends them all. Requires role MIGRATE_ACTIVITIES",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "OK",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Invalid request",
        content = [
          Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class)),
        ],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [
          Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class)),
        ],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden, requires role NOMIS_ACTIVITIES",
        content = [
          Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class)),
        ],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Not found",
        content = [
          Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class)),
        ],
      ),
    ],
  )
  suspend fun endMigratedActivities(
    @Schema(description = "Migration ID", type = "string") @PathVariable migrationId: String,
  ) = activitiesMigrationService.endMigratedActivities(migrationId)

  @PreAuthorize("hasRole('ROLE_MIGRATE_ACTIVITIES')")
  @PutMapping("/{migrationId}/filter")
  @Operation(
    summary = "Update the filter for a migration history record",
    description = "Requires role <b>ROLE_MIGRATE_ACTIVITIES</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Filter updated",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "Migration not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun updateFilter(
    @PathVariable @Schema(description = "Migration Id", example = "2020-03-24T12:00:00") migrationId: String,
    @RequestBody @Valid migrationFilter: ActivitiesMigrationFilter,
  ) = activitiesMigrationService.updateFilter(migrationId, migrationFilter)
}
