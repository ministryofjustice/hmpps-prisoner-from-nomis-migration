package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance
import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
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
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__UPDATE__RW')")
@Tag(name = "Finance Migration Resource")
class PrisonBalanceDataRepairResource(
  private val prisonBalanceSynchronisationService: PrisonBalanceSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {

  @PostMapping("/prisons/{prisonId}/prison-balance/repair")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Resynchronises account balances for the given prison (Nomis prisonId) from NOMIS to DPS",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW",
  )
  suspend fun repairPrisonBalance(@PathVariable prisonId: String) {
    try {
      prisonBalanceSynchronisationService.resynchronisePrisonBalance(prisonId)
      telemetryClient.trackEvent(
        "prisonbalance-resynchronisation-repair",
        mapOf(
          "prisonId" to prisonId,
        ),
      )
    } catch (_: NotFound) {
      throw NotFoundException("No prison balance for $prisonId was found")
    }
  }
}
