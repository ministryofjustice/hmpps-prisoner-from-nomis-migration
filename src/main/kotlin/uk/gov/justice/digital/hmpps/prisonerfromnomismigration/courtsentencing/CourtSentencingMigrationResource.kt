package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.HttpStatus.OK
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCases

@RestController
@Tag(name = "Court Sentencing Migration Resource")
@RequestMapping("/migrate", produces = [MediaType.APPLICATION_JSON_VALUE])
class CourtSentencingMigrationResource(
  private val courtSentencingMigrationService: CourtSentencingMigrationService,
) {
  @PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW')")
  @PostMapping("/court-sentencing")
  @ResponseStatus(value = ACCEPTED)
  @Operation(
    summary = "Starts a court case migration",
    description = "Starts an asynchronous migration process. This operation will return immediately and the migration will be performed asynchronously. Requires role <b>PRISONER_FROM_NOMIS__MIGRATION__RW</b>",
    requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
      content = [
        Content(
          mediaType = "application/json",
          schema = Schema(implementation = CourtSentencingMigrationFilter::class),
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
      ApiResponse(
        responseCode = "409",
        description = "Migration already in progress",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun migrateCourtSentencing(
    @RequestBody @Valid
    migrationFilter: CourtSentencingMigrationFilter,
  ) = courtSentencingMigrationService.startMigration(migrationFilter)

  @PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW')")
  @GetMapping("/court-sentencing/offender-payload/{offenderNo}")
  @ResponseStatus(value = OK)
  @Operation(
    summary = "provides the migration payload for debug purposes",
    description = "Provides the migration payload for an offender, no migration is performed. Useful for investigating migration errors. Requires role <b>PRISONER_FROM_NOMIS__MIGRATION__RW</b>",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Migration payload returned",
        content = [
          Content(
            mediaType = "application/json",
            schema = Schema(implementation = MigrationCreateCourtCases::class),
          ),
        ],
      ),
      ApiResponse(
        responseCode = "401",
        description = "Unauthorized to access this endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Incorrect permissions to call endpoint",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun offenderMigrationPayload(
    @Schema(description = "Offender No AKA prisoner number", example = "A1234AK")
    @PathVariable
    offenderNo: String,
  ) = courtSentencingMigrationService.offenderMigrationPayload(offenderNo)
}
