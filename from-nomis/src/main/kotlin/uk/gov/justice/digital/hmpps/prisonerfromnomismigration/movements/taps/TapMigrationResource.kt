package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapMigrationFilter
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapMigrationService

@RestController
@RequestMapping("/migrate/taps", produces = [MediaType.APPLICATION_JSON_VALUE])
@Tag(name = "Taps Migration Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW')")
class TapMigrationResource(
  private val migrationService: TapMigrationService,
  private val telemetryClient: TelemetryClient,
) {
  @PostMapping
  @ResponseStatus(value = HttpStatus.ACCEPTED)
  @Operation(
    summary = "Starts a tap full repair",
    description = "Starts an asynchronous migration process to repair TAPs for all prisoners. This has been repurposed from the TAP migration. Requires role <b>PRISONER_FROM_NOMIS__MIGRATION__RW</b> ot <b>PRISONER_FROM_NOMIS__MIGRATION__RW</b>",
    responses = [
      ApiResponse(
        responseCode = "202",
        description = "Full TAP repair process started",
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
  suspend fun migrateTaps(
    @RequestBody @Valid migrationFilter: TapMigrationFilter,
  ) = migrationService.startMigration(migrationFilter)

  @PreAuthorize("hasAnyRole('ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW','ROLE_PRISONER_FROM_NOMIS__REPAIR_MOVEMENTS__RW')")
  @PutMapping("/repair/{prisonerNumber}")
  @Operation(
    summary = "Repair all TAP applications, schedules and movements for a single prisoner",
    description = "Resyncs a single prisoner to DPS. For prisoners with lots of movements this could be a lengthy process - maybe up to 2 minutes.",
    responses = [
      ApiResponse(
        responseCode = "200",
        description = "Prisoner re-synchronised",
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
        responseCode = "404",
        description = "Prisoner not found in NOMIS",
        content = [Content(mediaType = "application/json", schema = Schema(implementation = ErrorResponse::class))],
      ),
    ],
  )
  suspend fun repairPrisonerTaps(
    @Schema(description = "The prisoner to resync")
    @PathVariable prisonerNumber: String,
  ) {
    telemetryClient.trackEvent(
      "temporary-absences-migration-entity-repair-requested",
      mapOf("offenderNo" to prisonerNumber),
      null,
    )

    migrationService.resyncPrisonerTaps(prisonerNumber)
  }
}
