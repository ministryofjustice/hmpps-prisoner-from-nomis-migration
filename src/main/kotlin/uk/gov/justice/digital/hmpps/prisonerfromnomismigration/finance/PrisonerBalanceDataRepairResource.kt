package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance
import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
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
class PrisonerBalanceDataRepairResource(
  private val prisonerBalanceSynchronisationService: PrisonerBalanceSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {

  @PostMapping("/prisoners/{rootOffenderId}/prisoner-balance/repair")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ROLE_MIGRATE_NOMIS_SYSCON')")
  @Operation(
    summary = "Resynchronises account balances for the given prisoner (Nomis rootOffenderId) from NOMIS to DPS",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_MIGRATE_NOMIS_SYSCON",
  )
  suspend fun repairPrisonerBalance(@PathVariable rootOffenderId: Long) {
    try {
      prisonerBalanceSynchronisationService.resynchronisePrisonerBalance(rootOffenderId)
      telemetryClient.trackEvent(
        "prisonerbalance-resynchronisation-repair",
        mapOf(
          "rootOffenderId" to rootOffenderId,
        ),
      )
    } catch (_: NotFound) {
      throw NotFoundException("No prisoner balance for $rootOffenderId was found")
    }
  }
}
