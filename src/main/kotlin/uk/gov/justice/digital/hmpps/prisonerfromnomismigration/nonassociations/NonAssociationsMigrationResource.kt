package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.HistoryFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InProgressMigration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import java.time.LocalDateTime

@RestController
@RequestMapping("/migrate", produces = [MediaType.APPLICATION_JSON_VALUE])
class NonAssociationsMigrationResource(
  private val nonAssociationsMigrationService: NonAssociationsMigrationService,
  private val migrationHistoryService: MigrationHistoryService,
) {
  @PreAuthorize("hasRole('ROLE_MIGRATE_NON_ASSOCIATIONS')")
  @PostMapping("/non-associations")
  @ResponseStatus(value = ACCEPTED)
  @Operation(
    summary = "Starts an nonAssociations migration",
    description = "Starts an asynchronous migration process. This operation will return immediately and the migration will be performed asynchronously. Requires role <b>MIGRATE_NON_ASSOCIATIONS</b>",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = NonAssociationsMigrationFilter::class),
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
    ],
  )
  suspend fun migrateNonAssociations(
    @RequestBody @Valid
    migrationFilter: NonAssociationsMigrationFilter,
  ) = nonAssociationsMigrationService.startMigration(migrationFilter)

  @PreAuthorize("hasRole('ROLE_MIGRATE_NON_ASSOCIATIONS')")
  @GetMapping("/non-associations/history")
  @Operation(
    summary = "Lists all filtered migration history records un-paged for nonAssociations",
    description = "The records are un-paged and requires role <b>MIGRATE_NON_ASSOCIATIONS</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "All migration history records",
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
  suspend fun getAll(
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
    ) @RequestParam includeOnlyFailures: Boolean = false,
  ) = migrationHistoryService.findAll(
    HistoryFilter(
      migrationTypes = listOf(MigrationType.NON_ASSOCIATIONS.name),
      fromDateTime = fromDateTime,
      toDateTime = toDateTime,
      includeOnlyFailures = includeOnlyFailures,
    ),
  )

  @PreAuthorize("hasRole('ROLE_MIGRATE_NON_ASSOCIATIONS')")
  @GetMapping("/non-associations/history/{migrationId}")
  @Operation(
    summary = "Gets a specific migration history record",
    description = "Requires role <b>MIGRATE_NON_ASSOCIATIONS</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "The migration history record",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = MigrationHistory::class),
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
      ApiResponse(
        responseCode = "404",
        description = "Migration not found",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun get(
    @PathVariable
    @Schema(description = "Migration Id", example = "2020-03-24T12:00:00", required = true)
    migrationId: String,
  ) = migrationHistoryService.get(migrationId)

  @PreAuthorize("hasRole('ROLE_MIGRATE_NON_ASSOCIATIONS')")
  @PostMapping("/non-associations/{migrationId}/cancel")
  @ResponseStatus(value = ACCEPTED)
  @Operation(
    summary = "Cancels a running migration. The actual cancellation might take several minutes to complete",
    description = "Requires role <b>MIGRATE_NON_ASSOCIATIONS</b>",
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
        description = "No running migration found with migration id",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun cancel(
    @PathVariable
    @Schema(description = "Migration Id", example = "2020-03-24T12:00:00", required = true)
    migrationId: String,
  ) = nonAssociationsMigrationService.cancel(migrationId)

  @PreAuthorize("hasRole('ROLE_MIGRATE_NON_ASSOCIATIONS')")
  @GetMapping("/non-associations/active-migration")
  @Operation(
    summary = "Gets active/currently running migration data, using migration record and migration queues",
    description = "Requires role <b>MIGRATE_NON_ASSOCIATIONS</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Only called during an active migration from the UI - assumes latest migration is active",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = InProgressMigration::class),
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
  suspend fun getActiveMigrationDetails() = migrationHistoryService.getActiveMigrationDetails(MigrationType.NON_ASSOCIATIONS)
}
