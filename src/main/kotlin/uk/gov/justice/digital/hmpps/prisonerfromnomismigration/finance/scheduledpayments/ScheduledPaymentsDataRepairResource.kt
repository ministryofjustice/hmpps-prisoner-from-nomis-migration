package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.scheduledpayments

import com.microsoft.applicationinsights.TelemetryClient
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent

@RestController
@Tag(name = "Finance Migration Resource")
@PreAuthorize("hasRole('ROLE_PRISONER_FROM_NOMIS__UPDATE__RW')")
class ScheduledPaymentsDataRepairResource(
  private val service: ScheduledPaymentsSynchronisationService,
  private val telemetryClient: TelemetryClient,
) {

  @PostMapping("/prisoners/scheduled-payments/{scheduledPaymentId}/repair")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
    summary = "Resynchronises a prisoner scheduled payment for the given scheduled payment id",
    description = """
      Used when an unexpected event has happened in NOMIS that has resulted in the DPS data drifting from NOMIS, so emergency use only. 
      Requires ROLE_PRISONER_FROM_NOMIS__UPDATE__RW
      """,
  )
  suspend fun repairPrisonerScheduledPayment(
    @Schema(description = "Prisoner scheduled payment id (offender_profile_payments_id)", example = "123456", required = true)
    @PathVariable scheduledPaymentId: Long,
  ) {
    service.resynchroniseScheduledPayment(scheduledPaymentId)
    telemetryClient.trackEvent(
      "finance-scheduled-payments-resynchronisation-repair",
      mapOf(
        "scheduledPaymentId" to scheduledPaymentId,
      ),
    )
  }
}
