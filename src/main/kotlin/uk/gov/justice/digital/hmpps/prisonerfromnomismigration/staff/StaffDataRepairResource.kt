package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

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
@Tag(name = "Staff Migration Resource")
class StaffDataRepairResource(
  private val staffSynchronisationService: StaffSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {
  @PostMapping("/staff/{staffId}/repair")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Resynchronises details for the given staff (Nomis staffId) from NOMIS to DPS",
    description = """Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only.
       Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW""",
  )
  suspend fun repairStaff(@PathVariable staffId: Long) {
    try {
      staffSynchronisationService.resynchroniseStaff(staffId)
      telemetryClient.trackEvent(
        "staff-resynchronisation-repair",
        mapOf(
          "staffId" to staffId,
        ),
      )
    } catch (_: NotFound) {
      throw NotFoundException("No staff for nomisStaffId $staffId was found")
    }
  }
}
