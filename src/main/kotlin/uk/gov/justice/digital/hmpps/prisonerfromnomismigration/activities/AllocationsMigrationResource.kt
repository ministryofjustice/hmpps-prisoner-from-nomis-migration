package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.FindActiveAllocationIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.HistoryFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import java.time.LocalDateTime

@RestController
@RequestMapping("/migrate", produces = [MediaType.APPLICATION_JSON_VALUE])
class AllocationsMigrationResource(
  private val migrationHistoryService: MigrationHistoryService,
  private val allocationsMigrationService: AllocationsMigrationService,
) {
  @PreAuthorize("hasRole('ROLE_MIGRATE_ACTIVITIES')")
  @PostMapping("/allocations")
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  @Operation(
    summary = "Starts an allocations migration",
    description = "Starts an asynchronous migration process. This operation will return immediately and the migration will be performed asynchronously. Requires role <b>MIGRATE_ACTIVITIES</b>",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = AllocationsMigrationFilter::class),
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
  suspend fun migrateAllocations(
    @RequestBody @Valid
    migrationFilter: AllocationsMigrationFilter,
  ) = allocationsMigrationService.startMigration(migrationFilter)

  @PreAuthorize("hasRole('ROLE_MIGRATE_ACTIVITIES')")
  @GetMapping("/allocations/history")
  @Operation(
    summary = "Lists all filtered migration history records un-paged for allocations",
    description = "The records are un-paged and requires role <b>MIGRATE_ACTIVITIES</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "All migration history records",
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
      migrationTypes = listOf(MigrationType.ALLOCATIONS.name),
      fromDateTime = fromDateTime,
      toDateTime = toDateTime,
      includeOnlyFailures = includeOnlyFailures,
    ),
  )

  @PreAuthorize("hasRole('ROLE_MIGRATE_ACTIVITIES')")
  @GetMapping("/allocations/history/{migrationId}")
  @Operation(
    summary = "Gets a specific migration history record",
    description = "Requires role <b>MIGRATE_ACTIVITIES</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "The migration history record",
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
    @Schema(description = "Migration Id", example = "2020-03-24T12:00:00")
    migrationId: String,
  ) = migrationHistoryService.get(migrationId)

  @PreAuthorize("hasRole('ROLE_MIGRATE_ACTIVITIES')")
  @PostMapping("/allocations/{migrationId}/cancel")
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  @Operation(
    summary = "Cancels a running migration. The actual cancellation might take several minutes to complete",
    description = "Requires role <b>MIGRATE_ACTIVITIES</b>",
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
    @Schema(description = "Migration Id", example = "2020-03-24T12:00:00")
    migrationId: String,
  ) = allocationsMigrationService.cancel(migrationId)

  @PreAuthorize("hasRole('ROLE_MIGRATE_ACTIVITIES')")
  @GetMapping("/allocations/active-migration")
  @Operation(
    summary = "Gets active/currently running migration data, using migration record and migration queues",
    description = "Requires role <b>MIGRATE_ACTIVITIES</b>",
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
    ],
  )
  suspend fun getActiveMigrationDetails() = migrationHistoryService.getActiveMigrationDetails(MigrationType.ALLOCATIONS)

  @PreAuthorize("hasRole('ROLE_MIGRATE_ACTIVITIES')")
  @GetMapping("/allocations/ids")
  @Operation(
    summary = "Find paged allocations eligible for migration",
    description = "Searches for active offender program profiles for active course activities (excluding DPS program services). Requires role MIGRATE_ACTIVITIES",
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
  suspend fun findAllocationsToMigrate(
    @Schema(description = "Page number")
    @RequestParam(defaultValue = "0")
    page: Long,
    @Schema(description = "Page size")
    @RequestParam(defaultValue = "10")
    size: Long,
    @Schema(description = "Prison id") @RequestParam prisonId: String,
    @Schema(description = "Course Activity ID", type = "integer") @RequestParam courseActivityId: Long?,
  ): Page<FindActiveAllocationIdsResponse> =
    allocationsMigrationService.getIds(AllocationsMigrationFilter(prisonId, courseActivityId), size, page)
}
