package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent

@Service
class CorePersonSynchronisationBeliefsService(
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {

  suspend fun offenderBeliefAdded(event: OffenderBeliefEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "rootOffenderId" to event.rootOffenderId,
      "nomisOffenderBeliefId" to event.offenderBeliefId,
    )
    telemetryClient.trackEvent("coreperson-belief-synchronisation-added-notimplemented", telemetry)
  }

  suspend fun offenderBeliefUpdated(event: OffenderBeliefEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "rootOffenderId" to event.rootOffenderId,
      "nomisOffenderBeliefId" to event.offenderBeliefId,
    )
    telemetryClient.trackEvent("coreperson-belief-synchronisation-updated-notimplemented", telemetry)
  }

  suspend fun offenderBeliefDeleted(event: OffenderBeliefEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "rootOffenderId" to event.rootOffenderId,
      "nomisOffenderBeliefId" to event.offenderBeliefId,
    )
    telemetryClient.trackEvent("coreperson-belief-synchronisation-deleted-notimplemented", telemetry)
  }
}
