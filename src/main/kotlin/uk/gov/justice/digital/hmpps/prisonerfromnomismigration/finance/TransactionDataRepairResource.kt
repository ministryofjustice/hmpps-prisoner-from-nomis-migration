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
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__UPDATE__RW')")
@Tag(name = "Finance Migration Resource")
class TransactionDataRepairResource(
  private val transactionSynchronisationService: TransactionSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {

  @PostMapping("/transactions/{transactionId}/repair")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = """Resynchronises all associated transactions for the given transaction from NOMIS to DPS.
        This is for prison (GL) or prisoner (offender) transactions.
        It will create the transaction in dps if it doesn't exist, or update it if it already exists.""",
    description = """Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS,
       so emergency use only.
       Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW
       """,
  )
  suspend fun repairTransaction(@PathVariable transactionId: Long) {
    try {
      transactionSynchronisationService.resynchroniseTransaction(transactionId)
      telemetryClient.trackEvent(
        "transaction-resynchronisation-repair",
        mapOf(
          "transactionId" to transactionId,
        ),
      )
    } catch (_: NotFound) {
      throw NotFoundException("No transaction for $transactionId was found")
    }
  }
}
