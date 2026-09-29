package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

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
@Tag(name = "Finance Migration Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__UPDATE__RW')")
class HoldsDataRepairResource(
  private val service: HoldsSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {

  @PostMapping("/holds/{transactionId}/repair")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = """Resynchronises a hold for the given transaction from NOMIS to DPS.
        It does not resync the transaction itself, only the hold data.
        This is for hold transactions.
        It will create the hold in dps if it doesn't exist, or update it if it already exists.""",
    description = """Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS,
       so emergency use only.
       Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW
       """,
  )
  suspend fun repairHold(@PathVariable transactionId: Long) {
    try {
      service.resynchroniseHold(transactionId)
      telemetryClient.trackEvent("hold-resynchronisation-repair", mapOf("transactionId" to transactionId))
    } catch (_: NotFound) {
      throw NotFoundException("No hold transaction for $transactionId was found")
    }
  }
}
