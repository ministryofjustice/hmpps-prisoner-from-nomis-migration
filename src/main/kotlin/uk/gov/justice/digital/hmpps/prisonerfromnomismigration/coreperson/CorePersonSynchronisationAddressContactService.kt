package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent

@Service
class CorePersonSynchronisationAddressContactService(
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {
  suspend fun offenderEmailAdded(event: OffenderEmailEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisOffenderId" to event.offenderId,
      "nomisInternetAddressId" to event.internetAddressId,
    )
    telemetryClient.trackEvent("coreperson-email-synchronisation-added-notimplemented", telemetry)
  }

  suspend fun offenderEmailUpdated(event: OffenderEmailEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisOffenderId" to event.offenderId,
      "nomisInternetAddressId" to event.internetAddressId,
    )
    telemetryClient.trackEvent("coreperson-email-synchronisation-updated-notimplemented", telemetry)
  }

  suspend fun offenderEmailDeleted(event: OffenderEmailEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisOffenderId" to event.offenderId,
      "nomisInternetAddressId" to event.internetAddressId,
    )
    telemetryClient.trackEvent("coreperson-email-synchronisation-deleted-notimplemented", telemetry)
  }
}
