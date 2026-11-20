package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.transactions

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus.OK
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NotFoundException

@RestController
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__UPDATE__RW')")
@Tag(name = "Finance Migration Resource")
class PrisonTransactionDataRepairResource(
  private val service: TransactionSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {
  @GetMapping("/prison-transactions/{transactionId}/repair")
  @ResponseStatus(value = OK)
  @Operation(
    summary = """Resynchronises all associated prison transactions for the given transaction from NOMIS to DPS.
        This is for prison (GL) transactions with no associated offender transaction, so currently does not resync the
        actual transaction with Dps.  It will however resync any associated processing (such as advance write-offs).
        """,
    description = """Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS,
       so emergency use only.
       Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW
      """,
  )
  suspend fun resyncPrisonTransaction(@PathVariable transactionId: Long) {
    try {
      service.resynchronisePrisonTransaction(transactionId)
      telemetryClient.trackEvent(
        "transaction-resynchronisation-repair",
        mapOf(
          "transactionId" to transactionId,
        ),
      )
    } catch (_: NotFound) {
      throw NotFoundException("No prison transaction for $transactionId was found")
    }
  }
}
