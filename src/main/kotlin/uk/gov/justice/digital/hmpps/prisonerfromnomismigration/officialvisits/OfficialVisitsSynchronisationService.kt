package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent

@Service
class OfficialVisitsSynchronisationService(
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {
  fun visitAdded(event: VisitEvent) = track("officialvisits-visit-synchronisation-created", event.asTelemetry()) {}
  fun visitUpdated(event: VisitEvent) = track("officialvisits-visit-synchronisation-updated", event.asTelemetry()) {}
  fun visitDeleted(event: VisitEvent) = track("officialvisits-visit-synchronisation-deleted", event.asTelemetry()) {}
  fun visitorAdded(event: VisitVisitorEvent) {
    if (event.personId == null) {
      telemetryClient.trackEvent("officialvisits-visitor-synchronisation-created-ignored", event.asTelemetry())
    } else {
      track("officialvisits-visitor-synchronisation-created", event.asTelemetry()) {}
    }
  }
  fun visitorUpdated(event: VisitVisitorEvent) {
    if (event.personId == null) {
      telemetryClient.trackEvent("officialvisits-visitor-synchronisation-updated-ignored", event.asTelemetry())
    } else {
      track("officialvisits-visitor-synchronisation-updated", event.asTelemetry()) {}
    }
  }
  fun visitorDeleted(event: VisitVisitorEvent) {
    if (event.personId == null) {
      telemetryClient.trackEvent("officialvisits-visitor-synchronisation-deleted-ignored", event.asTelemetry())
    } else {
      track("officialvisits-visitor-synchronisation-deleted", event.asTelemetry()) {}
    }
  }
}

fun VisitEvent.asTelemetry() = mutableMapOf<String, Any>(
  "nomisVisitId" to visitId,
  "offenderNo" to offenderIdDisplay,
  "prisonId" to agencyLocationId,
  "bookingId" to bookingId,
)

fun VisitVisitorEvent.asTelemetry() = mutableMapOf<String, Any>(
  "nomisVisitorId" to visitVisitorId,
  "nomisVisitId" to visitId,
  "offenderNo" to offenderIdDisplay,
  "bookingId" to bookingId,
).let {
  if (personId != null) {
    it["nomisPersonId"] = personId
  }
  it
}
