package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.resources

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrateService

@RestController
@RequestMapping("/migrate", produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW')")
class MigrateResource(
  private val migrateService: MigrateService,
) {
  @PostMapping("/cancel/{migrationId}")
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  @Operation(
    summary = "Cancels a running migration. The actual cancellation might take several minutes to complete",
    description = "Requires role <b>ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW</b>",
    responses = [
      ApiResponse(
        responseCode = "202",
        description = "Cancellation request accepted",
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
  suspend fun cancel(
    @PathVariable @Schema(description = "Migration Id", example = "2020-03-24T12:00:00") migrationId: String,
  ) = migrateService.cancel(migrationId)

  @PostMapping("/refresh/{migrationId}")
  @ResponseStatus(value = HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Refreshes the statistics on a completed migration.",
    description = """This will read from the mapping table to get the latest count of migration records.
      It will also then count the number of failures on the main queue and update the history record.
      This is useful if the migration has been marked as completed when there were still messages on the dead letter
      queue that where then processed later.
      Requires role <b>ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW</b>""",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Refresh completed",
      ),
      ApiResponse(
        responseCode = "400",
        description = "Migration is not in a completed state",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
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
  suspend fun refresh(
    @PathVariable @Schema(description = "Migration Id", example = "2020-03-24T12:00:00") migrationId: String,
  ) = migrateService.refresh(migrationId)
}
