package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track

@Service
class OfficialVisitsSynchronisationService(
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {
  fun visitAdded(event: VisitEvent) = track("officialvisits-visit-synchronisation-created", event.asTelemetry()) {}
  fun visitUpdated(event: VisitEvent) = track("officialvisits-visit-synchronisation-updated", event.asTelemetry()) {}
  fun visitDeleted(event: VisitEvent) = track("officialvisits-visit-synchronisation-deleted", event.asTelemetry()) {}
}

fun VisitEvent.asTelemetry() = mutableMapOf<String, Any>(
  "nomisVisitId" to visitId,
  "offenderNo" to offenderIdDisplay,
  "prisonId" to agencyLocationId,
  "bookingId" to bookingId,
)
