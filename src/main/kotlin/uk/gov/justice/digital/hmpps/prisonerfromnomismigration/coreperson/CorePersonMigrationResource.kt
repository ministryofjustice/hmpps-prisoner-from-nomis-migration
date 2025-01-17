package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.HistoryFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType

@RestController
@RequestMapping("/migrate/core-person", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "core-person-migration-resource")
@PreAuthorize("hasAnyRole('ROLE_MIGRATE_CORE_PERSON', 'ROLE_MIGRATE_NOMIS_SYSCON')")
class CorePersonMigrationResource(
  private val migrationService: CorePersonMigrationService,
  private val migrationHistoryService: MigrationHistoryService,
) {
  @PostMapping
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  @Operation(
    summary = "Starts a core person migration. The entity type is determined by the migration filter",
    description = "Starts an asynchronous migration process. This operation will return immediately and the migration will be performed asynchronously. Requires role <b>MIGRATE_CORE_PERSON</b>",
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
  suspend fun migrateCorePerson(
    @RequestBody @Valid migrationFilter: CorePersonMigrationFilter,
  ) = migrationService.startMigration(migrationFilter)

  @GetMapping("/history")
  @Operation(
    summary = "Lists all migration history records un-paged for core person",
    description = "The records are un-paged and requires role <b>MIGRATE_CORE_PERSON</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "All core person migration history records",
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
  suspend fun getAll() = migrationHistoryService.findAll(HistoryFilter(migrationTypes = listOf(MigrationType.CORE_PERSON.name)))

  @GetMapping("/history/{migrationId}")
  @Operation(
    summary = "Gets a specific migration history record",
    description = "Requires role <b>MIGRATE_CORE_PERSON</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "The core person migration history record",
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

  @PostMapping("/{migrationId}/cancel")
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  @Operation(
    summary = "Cancels a running migration. The actual cancellation might take several minutes to complete",
    description = "Requires role <b>MIGRATE_CORE_PERSON</b>",
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
  ) = migrationService.cancel(migrationId)

  @GetMapping("/active-migration")
  @Operation(
    summary = "Gets active/currently running migration data, using migration record and migration queues",
    description = "Requires role <b>MIGRATE_CORE_PERSON</b>",
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
  suspend fun activeMigration() = migrationHistoryService.getActiveMigrationDetails(MigrationType.CORE_PERSON)
}
