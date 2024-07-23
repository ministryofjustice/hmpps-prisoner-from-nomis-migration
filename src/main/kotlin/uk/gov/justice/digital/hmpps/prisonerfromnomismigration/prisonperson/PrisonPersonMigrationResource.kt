package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
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
@RequestMapping("/migrate/prisonperson", produces = [MediaType.APPLICATION_JSON_VALUE])
class PrisonPersonMigrationResource(
  private val prisonPersonMigrationService: PrisonPersonMigrationService,
  private val migrationHistoryService: MigrationHistoryService,
) {
  @PreAuthorize("hasRole('ROLE_MIGRATE_PRISONPERSON')")
  @PostMapping("/physical-attributes")
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  @Operation(
    summary = "Starts an physical attributes migration",
    description = "Starts an asynchronous migration process. This operation will return immediately and the migration will be performed asynchronously. Requires role <b>MIGRATE_PRISONPERSON</b>",
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
  suspend fun migratePhysicalAttributes(
    @RequestBody @Valid migrationFilter: PrisonPersonMigrationFilter,
  ) = prisonPersonMigrationService.startMigration(migrationFilter)

  @PreAuthorize("hasRole('ROLE_MIGRATE_PRISONPERSON')")
  @GetMapping("/history")
  @Operation(
    summary = "Lists all migration history records un-paged for prison person",
    description = "The records are un-paged and requires role <b>MIGRATE_PRISONPERSON</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "All prison person migration history records",
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
  suspend fun getAll() = migrationHistoryService.findAll(HistoryFilter(migrationTypes = listOf(MigrationType.PRISONPERSON.name)))

  @PreAuthorize("hasRole('ROLE_MIGRATE_PRISONPERSON')")
  @GetMapping("/history/{migrationId}")
  @Operation(
    summary = "Gets a specific migration history record",
    description = "Requires role <b>MIGRATE_PRISONPERSON</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "The prison person migration history record",
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
}
