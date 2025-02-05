package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent

@RestController
class CaseNotesDataRepairResource(
  private val caseNotesSynchronisationService: CaseNotesSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {
  @DeleteMapping("/casenotes/{nomisCaseNoteId}/repair")
  @ResponseStatus(HttpStatus.OK)
  @PreAuthorize("hasRole('ROLE_MIGRATE_CASENOTES')")
  @Operation(
    summary = "Repairs a casenote that has been deleted in Nomis by removing any associated mappings in the mapping table and alerting DPS",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_MIGRATE_CASENOTES",
  )
  suspend fun repairAlert(@PathVariable nomisCaseNoteId: Long) {
    caseNotesSynchronisationService.repairDeletedCaseNote(nomisCaseNoteId)
    telemetryClient.trackEvent(
      "casenotes-repair-deleted-success",
      mapOf(
        "nomisCaseNoteId" to nomisCaseNoteId,
      ),
    )
  }
}
