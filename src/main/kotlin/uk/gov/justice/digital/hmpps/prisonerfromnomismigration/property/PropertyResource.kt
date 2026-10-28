package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse

@RestController
@Tag(name = "Property Container Resource")
@ApiResponses(
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
)
class PropertyResource(
  private val migrationService: PropertyMigrationService,
  private val syncService: PropertySyncService,
) {
  @PostMapping("/migrate/property", produces = [MediaType.APPLICATION_JSON_VALUE])
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  @PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW')")
  @Operation(
    summary = "Starts a Property migration.",
    description = "Starts an asynchronous migration process. This operation will return immediately and the migration will be performed asynchronously. Requires role <b>PRISONER_FROM_NOMIS__MIGRATION__RW</b>",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(mediaType = "application/json", schema = Schema(implementation = PropertyMigrationFilter::class)),
      ],
    ),
    responses = [
      ApiResponse(responseCode = "202", description = "Migration process started"),
      ApiResponse(
        responseCode = "409",
        description = "Migration already in progress",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun startPropertyMigration(
    @RequestBody @Valid
    migrationFilter: PropertyMigrationFilter,
  ) = migrationService.startMigration(migrationFilter)

  @PutMapping("/property/{id}/repair")
  @PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__UPDATE__RW')")
  @Operation(
    summary = "Sync a property from Nomis to DPS.",
    description = "Update if the mapping exists, otherwise create. Requires role <b>ROLE_PRISONER_FROM_NOMIS__UPDATE__RW</b>",
  )
  suspend fun repair(
    @PathVariable id: Long,
  ) = syncService.repair(id)
}
