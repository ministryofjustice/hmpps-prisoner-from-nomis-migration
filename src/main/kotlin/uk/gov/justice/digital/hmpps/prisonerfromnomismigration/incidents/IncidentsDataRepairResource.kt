package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Incidents Migration Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__UPDATE__RW')")
class IncidentsDataRepairResource(
  private val incidentsSynchronisationService: IncidentsSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {
  @PostMapping("/incidents/{incidentId}/repair")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Resynchronises an existing incident from NOMIS back to DPS",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW",
  )
  suspend fun repairIncident(
    @PathVariable incidentId: Long,
    @Schema(description = "if true, will attempt to create a new incident")
    @RequestParam(value = "createIncident", required = false, defaultValue = "false") createIncident: Boolean,
    @Schema(description = "if true, will ignore the Nomis Agency Services Switch - so will always repair to DPS")
    @RequestParam(value = "overrideAgencySwitch", required = false, defaultValue = "false") overrideAgencySwitch: Boolean,
  ) {
    if (createIncident) {
      incidentsSynchronisationService.synchroniseIncidentInsert(
        IncidentsOffenderEvent(
          incidentId,
          null,
        ),
        overrideAgencySwitch,
      )
    } else {
      incidentsSynchronisationService.synchroniseIncidentUpdate(
        IncidentsOffenderEvent(
          incidentId,
          null,
        ),
        overrideAgencySwitch,
      )
    }
    telemetryClient.trackEvent(
      "incidents-resynchronisation-repair",
      mapOf("nomisIncidentId" to incidentId.toString()),
      null,
    )
  }
}
