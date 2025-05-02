package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class IncidentsDataRepairResource(
  private val incidentsSynchronisationService: IncidentsSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {
  @PostMapping("/incidents/{incidentId}/repair")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_MIGRATE_INCIDENT_REPORTS')")
  @Operation(
    summary = "Resynchronises an existing incident from NOMIS back to DPS",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_MIGRATE_INCIDENT_REPORTS",
  )
  suspend fun repairIncident(
    @PathVariable incidentId: Long,
    @Schema(description = "if true, will attempt to create a new incident")
    @RequestParam(value = "createIncident", required = false, defaultValue = "false") createIncident: Boolean,
  ) {
    if (createIncident) {
      incidentsSynchronisationService.synchroniseIncidentInsert(
        IncidentsOffenderEvent(
          incidentId,
          null,
        ),
      )
    } else {
      incidentsSynchronisationService.synchroniseIncidentUpdate(
        IncidentsOffenderEvent(
          incidentId,
          null,
        ),
      )
    }
    telemetryClient.trackEvent(
      "incidents-resynchronisation-repair",
      mapOf("nomisIncidentId" to incidentId.toString()),
      null,
    )
  }
}
