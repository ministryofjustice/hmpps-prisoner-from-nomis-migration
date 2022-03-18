package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.resources

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.HistoryFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService
import java.time.LocalDateTime

@RestController
@RequestMapping("/", produces = [MediaType.APPLICATION_JSON_VALUE])
class MigrationHistoryResource(private val migrationHistoryService: MigrationHistoryService) {
  @PreAuthorize("hasRole('ROLE_MIGRATION_ADMIN')")
  @GetMapping("/history")
  @Operation(
    summary = "Lists all filtered migration history",
    description = "The records are un-paged and requires role <b>MIGRATION_ADMIN</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "All history records",
        content = [
          Content(
            mediaType = "application/json",
            array = ArraySchema(schema = Schema(implementation = MigrationHistory::class))
          )
        ],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  fun getAll(
    @Parameter(
      description = "List of migration types, when omitted all migration types will be returned",
      example = "VISITS",
    ) @RequestParam migrationTypes: List<String>? = null,

    @Parameter(
      description = "Only include migrations started after this date time",
      example = "2020-03-23T12:00:00",
    )
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @RequestParam fromDateTime: LocalDateTime? = null,

    @Parameter(
      description = "Only include migrations started before this date time",
      example = "2020-03-24T12:00:00",
    )
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    @RequestParam toDateTime: LocalDateTime? = null,

    @Parameter(
      description = "When true only include migrations that had at least one failure",
      example = "false",
    ) @RequestParam includeOnlyFailures: Boolean = false,

    @Parameter(
      description = "Specify a word of phrase that will appear in the filter related to the migration",
      example = "HEI",
    ) @RequestParam filterContains: String? = null,
  ) = migrationHistoryService.findAll(
    HistoryFilter(
      migrationTypes = migrationTypes,
      fromDateTime = fromDateTime,
      toDateTime = toDateTime,
      includeOnlyFailures = includeOnlyFailures,
      filterContains = filterContains
    )
  )

  @PreAuthorize("hasRole('ROLE_MIGRATION_ADMIN')")
  @DeleteMapping("/history")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Deletes all migration history records",
    description = "This is only required for test environments and requires role <b>MIGRATION_ADMIN</b>",
    responses = [
      ApiResponse(
        responseCode = "204",
        description = "All history records deleted",
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))]
      )
    ]
  )
  suspend fun deleteAll() =
    migrationHistoryService.deleteAll()
}
