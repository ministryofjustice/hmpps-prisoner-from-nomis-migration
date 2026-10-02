package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NotFoundException

@RestController
@Tag(name = "Finance Migration Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__UPDATE__RW')")
class AdvancesDataRepairResource(
  private val service: PrisonerAdvanceSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {

  @PostMapping("/prisoners/advances/{advanceId}/repair")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Resynchronises a prisoner advance for the given prisoner advance id",
    description = """
      Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. 
      Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW
      """,
  )
  suspend fun repairPrisonerAdvance(
    @Schema(description = "Prisoner advance id (offender_profile_payments_id)", example = "123456", required = true)
    @PathVariable advanceId: Long,
  ) {
    try {
      service.resynchronisePrisonerAdvance(advanceId)
      telemetryClient.trackEvent(
        "prisoneradvance-resynchronisation-repair",
        mapOf(
          "advanceId" to advanceId,
        ),
      )
    } catch (_: NotFound) {
      throw NotFoundException("No prisoner advance for $advanceId was found")
    }
  }
}
