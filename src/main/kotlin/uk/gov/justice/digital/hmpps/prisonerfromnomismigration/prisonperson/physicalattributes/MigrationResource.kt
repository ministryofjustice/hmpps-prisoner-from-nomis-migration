package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.MigrationFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.MigrationService

@RestController("physicalAttributesMigrationResource")
@RequestMapping("/migrate/prisonperson/physical-attributes", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "prison-person-migration-resource")
class MigrationResource(
  @Qualifier("prisonPersonMigrationService") private val migrationService: MigrationService,
) {
  @PreAuthorize("hasRole('ROLE_MIGRATE_PRISONPERSON')")
  @PostMapping
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
    @RequestBody @Valid migrationFilter: MigrationFilter,
  ) = migrationService.startMigration(migrationFilter)
}
