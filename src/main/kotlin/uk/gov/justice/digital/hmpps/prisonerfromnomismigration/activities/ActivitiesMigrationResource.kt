package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Future
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import java.time.LocalDate

@RestController
@RequestMapping("/migrate/activities", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Activities Migration Resource")
@Validated
class ActivitiesMigrationResource(
  private val activitiesMigrationService: ActivitiesMigrationService,
) {
  @PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW')")
  @PostMapping
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  @Operation(
    summary = "Starts an activities migration",
    description = "Starts an asynchronous migration process. This operation will return immediately and the migration will be performed asynchronously. Requires role <b>PRISONER_FROM_NOMIS__MIGRATION__RW</b>",
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

  @PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW')")
  @PutMapping("/{migrationId}/end")
  @Operation(
    summary = "End all NOMIS activities and allocations for a migration on the day before the DPS activity start date",
    description = "Get all NOMIS activities migrated on a migrationId and ends them all on the day before the DPS activity start date. Requires role PRISONER_FROM_NOMIS__MIGRATION__RW",
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

  @PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW')")
  @PutMapping("/{migrationId}/move-start-dates")
  @Operation(
    summary = "Moves the start date for future activities in DPS.",
    description = "Update all DPS activities for this prison to the new start date. Get all NOMIS activities migrated move the end dates to the day before the new start date. Requires role PRISONER_FROM_NOMIS__MIGRATION__RW",
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
  suspend fun moveActivityStartDates(
    @Schema(description = "Migration ID", type = "string") @PathVariable migrationId: String,
    @Schema(description = "The new start date for all activities", example = "2023-01-01") @RequestBody @Valid request: MoveActivityStartDateRequest,
  ) = activitiesMigrationService.moveActivityStartDates(migrationId, request.newActivityStartDate)
}

data class MoveActivityStartDateRequest(
  @field:Future
  val newActivityStartDate: LocalDate,
)
