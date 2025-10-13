package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Visit Balance Migration Resource")
class VisitBalanceDataRepairResource(
  private val visitBalanceSynchronisationService: VisitBalanceSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {
  @PostMapping("/prisoners/{prisonNumber}/visit-balance/repair")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__UPDATE__RW')")
  @Operation(
    summary = "Resynchronises a visit balance for the given prisoner from NOMIS to DPS",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW",
  )
  suspend fun repairVisitBalance(@PathVariable prisonNumber: String) {
    visitBalanceSynchronisationService.resynchroniseVisitBalance(prisonNumber)
    telemetryClient.trackEvent(
      "visitbalance-resynchronisation-repair",
      mapOf(
        "prisonNumber" to prisonNumber,
      ),
      null,
    )
  }
}
