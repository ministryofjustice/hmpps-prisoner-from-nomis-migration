package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse

@RestController
@RequestMapping("/migrate/csras", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "CSRA Migration Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW')")
class CsraMigrationResource(
  private val migrationService: CsraMigrationService,
) {
  @PostMapping
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  @Operation(
    summary = "Starts a CSRA migration.",
    description = "Starts an asynchronous migration process. This operation will return immediately and the migration will be performed asynchronously. Requires role <b>PRISONER_FROM_NOMIS__MIGRATION__RW</b>",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(mediaType = "application/json", schema = Schema(implementation = PrisonerMigrationFilter::class)),
      ],
    ),
    responses = [
      ApiResponse(responseCode = "202", description = "Migration process started"),
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
      ApiResponse(
        responseCode = "409",
        description = "Migration already in progress",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun startMigration(
    @RequestBody @Valid
    migrationFilter: PrisonerMigrationFilter,
  ) = migrationService.startMigration(migrationFilter)
}
