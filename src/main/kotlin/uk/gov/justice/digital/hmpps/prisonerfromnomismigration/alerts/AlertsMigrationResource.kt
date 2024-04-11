package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.HistoryFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType

@RestController
@RequestMapping("/migrate", produces = [MediaType.APPLICATION_JSON_VALUE])
class AlertsMigrationResource(
  private val alertsMigrationService: AlertsMigrationService,
  private val alertsByPrisonerMigrationService: AlertsByPrisonerMigrationService,
  private val migrationHistoryService: MigrationHistoryService,
  @Value("\${alerts.migration.type}") private val migrationType: String,
) {
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
  suspend fun migrateAlerts(
    @RequestBody @Valid
    migrationFilter: AlertsMigrationFilter,
  ) = if (migrationType == "by-prisoner") alertsByPrisonerMigrationService.startMigration(migrationFilter) else alertsMigrationService.startMigration(migrationFilter)

  @PreAuthorize("hasRole('ROLE_MIGRATE_ALERTS')")
  @GetMapping("/alerts/history")
  @Operation(
    summary = "Lists all migration history records un-paged for alerts",
    description = "The records are un-paged and requires role <b>MIGRATE_ALERTS</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "All alerts migration history records",
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
  suspend fun getAll() = migrationHistoryService.findAll(HistoryFilter(migrationTypes = listOf(MigrationType.ALERTS.name)))

  @PreAuthorize("hasRole('ROLE_MIGRATE_ALERTS')")
  @GetMapping("/alerts/history/{migrationId}")
  @Operation(
    summary = "Gets a specific migration history record",
    description = "Requires role <b>MIGRATE_ALERTS</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "The alerts migration history record",
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

  @PreAuthorize("hasRole('ROLE_MIGRATE_ALERTS')")
  @PostMapping("/alerts/{migrationId}/cancel")
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  @Operation(
    summary = "Cancels a running migration. The actual cancellation might take several minutes to complete",
    description = "Requires role <b>MIGRATE_ALERTS</b>",
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
  ) = alertsMigrationService.cancel(migrationId)

  @PreAuthorize("hasRole('ROLE_MIGRATE_ALERTS')")
  @GetMapping("/alerts/active-migration")
  @Operation(
    summary = "Gets active/currently running migration data, using migration record and migration queues",
    description = "Requires role <b>MIGRATE_ALERTS</b>",
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
  suspend fun getActiveMigrationDetails() = migrationHistoryService.getActiveMigrationDetails(MigrationType.ALERTS)
}
