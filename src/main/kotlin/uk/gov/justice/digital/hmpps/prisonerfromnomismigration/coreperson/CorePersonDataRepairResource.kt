package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NotFoundException

@RestController
@Tag(name = "Core Person Repair Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__UPDATE__RW')")
class CorePersonDataRepairResource(
  private val synchronisationService: CorePersonSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {
  @PostMapping("/prisoners/{prisonNumber}/core-person/aliases-idenifiers/repair")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Resynchronises an offender's aliases and identifiers for the given prisoner from NOMIS to DPS",
    description = """
      Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, 
      so emergency use only. Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW""",
  )
  suspend fun repairCorePersonAliasesAndIdentifiers(@PathVariable prisonNumber: String) {
    try {
      synchronisationService.resynchroniseAliasesAndIdentifiers(prisonNumber)
    } catch (_: NotFound) {
      throw NotFoundException("Prisoner $prisonNumber not found")
    }
    telemetryClient.trackEvent(
      "core-person-aliases-identifiers-resynchronisation-repair",
      mapOf(
        "prisonNumber" to prisonNumber,
      ),
      null,
    )
  }
}
