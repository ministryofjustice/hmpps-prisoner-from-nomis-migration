package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.resources

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.HistoryFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import java.time.LocalDateTime

@RestController
@RequestMapping("/migrate/history", produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW')")
class MigrateHistoryResource(private val migrationHistoryService: MigrationHistoryService) {
  @GetMapping("/all/{migrationType}")
  @Operation(
    summary = "Lists all filtered migration history for the specified migration type",
    description = "The records are un-paged and requires role <b>ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "All history records for specified migration type",
        content = [
          Content(
            mediaType = "application/json",
            array = ArraySchema(schema = Schema(implementation = MigrationHistory::class)),
          ),
        ],
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
    ],
  )
  fun getHistory(
    @PathVariable @Schema(description = "Migration Type") migrationType: MigrationType,
    @Parameter(
      description = "Only include migrations started after this date time",
      example = "2020-03-23T12:00:00",
    )
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @RequestParam
    fromDateTime: LocalDateTime? = null,
    @Parameter(
      description = "Only include migrations started before this date time",
      example = "2020-03-24T12:00:00",
    )
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @RequestParam
    toDateTime: LocalDateTime? = null,
    @Parameter(
      description = "When true only include migrations that had at least one failure",
      example = "false",
    )
    @RequestParam includeOnlyFailures: Boolean = false,
    @Parameter(
      description = "Specify the prison associated with the migration",
      example = "HEI",
    )
    @RequestParam prisonId: String? = null,
  ) = migrationHistoryService.findAll(
    HistoryFilter(
      migrationTypes = listOf(migrationType.name),
      fromDateTime = fromDateTime,
      toDateTime = toDateTime,
      includeOnlyFailures = includeOnlyFailures,
      filterContains = prisonId?.let { """"prisonIds":["$it"]""" },
    ),
  )

  @GetMapping("/{migrationId}")
  @Operation(
    summary = "Gets a specific migration history record",
    description = "Requires role <b>ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "The visit balance migration history record",
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
  suspend fun get(
    @PathVariable @Schema(description = "Migration Id", example = "2020-03-24T12:00:00") migrationId: String,
  ) = migrationHistoryService.get(migrationId)

  @GetMapping("/active/{migrationType}")
  @Operation(
    summary = "Gets active/currently running migration data, using migration record and migration queues",
    description = "Requires role <b>ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Only called during an active migration from the UI - assumes latest migration is active",
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
  suspend fun activeMigration(
    @PathVariable @Schema(description = "Migration Type") migrationType: MigrationType,
  ) = migrationHistoryService.getActiveMigrationDetails(migrationType)
}
