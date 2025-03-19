package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent

@Service
class VisitBalanceSynchronisationService(
  private val telemetryClient: TelemetryClient,
) {

  suspend fun visitBalanceAdjustmentInserted(event: VisitBalanceOffenderEvent) {
    val telemetry = telemetryOf(
      "visitBalanceAdjustmentId" to event.visitBalanceAdjustmentId,
      "nomisPrisonNumber" to event.offenderIdDisplay,
    )
    telemetryClient.trackEvent("visitbalance-adjustment-synchronisation-inserted-notimplemented", telemetry)
  }

  suspend fun visitBalanceAdjustmentUpdated(event: VisitBalanceOffenderEvent) {
    val telemetry = telemetryOf(
      "visitBalanceAdjustmentId" to event.visitBalanceAdjustmentId,
      "nomisPrisonNumber" to event.offenderIdDisplay,
    )
    telemetryClient.trackEvent("visitbalance-adjustment-synchronisation-updated-notimplemented", telemetry)
  }

  suspend fun visitBalanceAdjustmentDeleted(event: VisitBalanceOffenderEvent) {
    val telemetry = telemetryOf(
      "visitBalanceAdjustmentId" to event.visitBalanceAdjustmentId,
      "nomisPrisonNumber" to event.offenderIdDisplay,
    )
    telemetryClient.trackEvent("visitbalance-adjustment-synchronisation-deleted-notimplemented", telemetry)
  }
}
