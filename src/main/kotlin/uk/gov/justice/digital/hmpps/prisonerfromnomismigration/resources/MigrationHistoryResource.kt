package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.resources

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService

@RestController
@RequestMapping("/", produces = [MediaType.APPLICATION_JSON_VALUE])
class MigrationHistoryResource(private val migrationHistoryService: MigrationHistoryService) {
  @PreAuthorize("hasRole('ROLE_MIGRATE_VISITS')")
  @GetMapping("/history")
  @Operation(
    summary = "Lists all migration history records un-paged",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "All history records",
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
  suspend fun getAll() =
    migrationHistoryService.findAll()

  @PreAuthorize("hasRole('ROLE_MIGRATION_ADMIN')")
  @DeleteMapping("/history")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Deletes all migration history records",
    description = "This is only required for test environments",
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
