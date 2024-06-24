package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class AlertsDataRepairResource(
  private val alertsSynchronisationService: AlertsSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {
  @PostMapping("/prisoners/{offenderNo}/alerts/resynchronise")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_MIGRATE_ALERTS')")
  @Operation(
    summary = "Resynchronises current alerts for the given prisoner from NOMIS back to DPS",
    description = "Used when unexpected event has happened in NOMIS that as resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_MIGRATE_ALERTS",
  )
  suspend fun repairAlerts(
    @PathVariable offenderNo: String,
  ) {
    alertsSynchronisationService.resynchronisePrisonerAlerts(offenderNo)
    telemetryClient.trackEvent(
      "from-nomis-synch-alerts-resynchronisation-repair",
      mapOf(
        "offenderNo" to offenderNo,
      ),
      null,
    )
  }
}
