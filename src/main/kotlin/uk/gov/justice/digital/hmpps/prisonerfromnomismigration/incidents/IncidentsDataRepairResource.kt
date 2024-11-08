package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class IncidentsDataRepairResource(
  private val incidentsSynchronisationService: IncidentsSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {
  @PostMapping("/incidents/{incidentId}/repair")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_MIGRATE_INCIDENTS')")
  @Operation(
    summary = "Resynchronises an existing incident from NOMIS back to DPS",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_MIGRATE_INCIDENTS",
  )
  suspend fun repairIncident(@PathVariable incidentId: Long) {
    incidentsSynchronisationService.synchroniseIncidentUpdate(
      IncidentsOffenderEvent(
        incidentId,
        null,
      ),
    )
    telemetryClient.trackEvent(
      "incidents-resynchronisation-repair",
      mapOf("nomisIncidentId" to incidentId.toString()),
      null,
    )
  }
}
