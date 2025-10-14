package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent

@RestController
@Tag(name = "Case Notes Migration Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__UPDATE__RW')")
class CaseNotesDataRepairResource(
  private val caseNotesSynchronisationService: CaseNotesSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {
  @DeleteMapping("/casenotes/{nomisCaseNoteId}/repair")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = """Repairs a casenote that has been deleted in Nomis by removing any associated mappings in the mapping table and alerting DPS.
      *** IMPORTANT*** This endpoint will delete any other associated Nomis Mappings (if matching the associated DPS Case Note) as there is a
      one to many mapping between DPS and Nomis case notes.
      Any related (deleted) Nomis case notes will be indicated with the casenotes-synchronisation-deleted-related-success telemetry event""",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW",
  )
  suspend fun repairDeletedCaseNote(@PathVariable nomisCaseNoteId: Long) {
    caseNotesSynchronisationService.repairDeletedCaseNote(nomisCaseNoteId)
    telemetryClient.trackEvent(
      "casenotes-repair-deleted-success",
      mapOf(
        "nomisCaseNoteId" to nomisCaseNoteId,
      ),
    )
  }
}
