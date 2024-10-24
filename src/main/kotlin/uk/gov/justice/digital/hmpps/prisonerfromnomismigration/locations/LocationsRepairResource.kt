package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/locations", produces = [MediaType.APPLICATION_JSON_VALUE])
@PreAuthorize("hasRole('ROLE_NOMIS_LOCATIONS')")
class LocationsRepairResource(
  private val locationsSynchronisationService: LocationsSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {
  @PostMapping("/id/{internalLocationId}/repair")
  @Operation(
    summary = "Resynchronises location from NOMIS to DPS, i.e. updates DPS with the data from Nomis",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_NOMIS_LOCATIONS",
  )
  suspend fun repairPunishments(
    @Schema(description = "Id of the location in Nomis")
    @PathVariable internalLocationId: Long,
    @Schema(description = "if true, record will be deleted in DPS and mapping table")
    @RequestParam(value = "recordDeleted", required = false, defaultValue = "false") recordDeleted: Boolean,
  ) {
    locationsSynchronisationService.synchroniseLocation(
      LocationsOffenderEvent(
        internalLocationId,
        null,
        null,
        null,
        null,
        recordDeleted,
      ),
    )
    telemetryClient.trackEvent(
      "locations-repair",
      mapOf(
        "nomisLocationId" to internalLocationId.toString(),
        "recordDeleted" to recordDeleted.toString(),
      ),
      null,
    )
  }
}
