package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import com.microsoft.applicationinsights.TelemetryClient
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Contact Person Migration Resource")
@PreAuthorize("hasAnyRole('ROLE_MIGRATE_CONTACTPERSON', 'ROLE_MIGRATE_NOMIS_SYSCON')")
class ContactPersonDataRepairResource(
  private val contactPersonSynchronisationService: ContactPersonSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {
  @PostMapping("/persons/{personId}/resynchronise")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Resynchronises person data from NOMIS back to DPS",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_MIGRATE_CONTACTPERSON or ROLE_MIGRATE_NOMIS_SYSCON",
  )
  suspend fun repairPerson(
    @PathVariable personId: Long,
  ) {
    contactPersonSynchronisationService.resetPersonForRepair(personId)
    telemetryClient.trackEvent(
      "from-nomis-synch-contactperson-resynchronisation-repair",
      mapOf(
        "personId" to personId.toString(),
      ),
      null,
    )
  }

  @PostMapping("/persons/{personId}/resynchronise-async")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
    summary = "Resynchronises person data from NOMIS back to DPS asynchronously",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only and when operation will take a very long time. Requires ROLE_MIGRATE_CONTACTPERSON or ROLE_MIGRATE_NOMIS_SYSCON",
  )
  suspend fun repairPersonAsync(
    @PathVariable personId: Long,
  ) {
    CoroutineScope(Dispatchers.Unconfined + Context.current().asContextElement()).launch {
      contactPersonSynchronisationService.resetPersonForRepair(personId)
      telemetryClient.trackEvent(
        "from-nomis-synch-contactperson-resynchronisation-repair",
        mapOf(
          "personId" to personId.toString(),
        ),
        null,
      )
    }
  }

  @PostMapping("/prisoners/{offenderNo}/contacts/resynchronise")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
    summary = "Resynchronises prisoner contacts from NOMIS back to DPS",
    description = "Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. Requires ROLE_MIGRATE_CONTACTPERSON or ROLE_MIGRATE_NOMIS_SYSCON",
  )
  suspend fun repairPrisonerContacts(
    @PathVariable offenderNo: String,
  ) {
    contactPersonSynchronisationService.resynchronizePrisonerContacts(offenderNo)
    telemetryClient.trackEvent(
      "from-nomis-synch-contactperson-prisoner-resynchronisation-repair",
      mapOf(
        "offenderNo" to offenderNo,
      ),
      null,
    )
  }
}
