package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationHistoryService

@RestController
@RequestMapping("/migrate/contact-person-profile-details", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "contact-person-migration-resource")
@PreAuthorize("hasAnyRole('ROLE_MIGRATE_CONTACTPERSON','ROLE_MIGRATE_NOMIS_SYSCON')")
class ContactPersonProfileDetailsMigrationResource(
  private val migrationService: ContactPersonProfileDetailsMigrationService,
  private val migrationHistoryService: MigrationHistoryService,
) {
  @PostMapping
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  @Operation(
    summary = "Starts a contact person profile details migration",
    description = "Starts an asynchronous migration process. This operation will return immediately and the migration will be performed asynchronously. Requires role <b>MIGRATE_CONTACTPERSON</b>",
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
  suspend fun migrateContactPerson(
    @RequestBody @Valid migrationFilter: ContactPersonProfileDetailsMigrationFilter,
  ) = migrationService.startMigration(migrationFilter)

  @GetMapping("/history/{migrationId}")
  @Operation(
    summary = "Gets a specific migration history record",
    description = "Requires role <b>MIGRATE_CONTACTPERSON</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "The contact person migration history record",
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
    description = "Requires role <b>MIGRATE_CONTACTPERSON</b>",
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
}
