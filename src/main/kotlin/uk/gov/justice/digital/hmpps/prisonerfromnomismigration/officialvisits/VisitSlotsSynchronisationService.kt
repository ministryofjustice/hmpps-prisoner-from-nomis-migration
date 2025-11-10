package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track

@Service
class VisitSlotsSynchronisationService(
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {
  fun visitTimeslotAdded(event: AgencyVisitTimeEvent) = track("officialvisits-timeslot-synchronisation-created", event.asTelemetry()) {}
  fun visitTimeslotUpdated(event: AgencyVisitTimeEvent) = track("officialvisits-timeslot-synchronisation-updated", event.asTelemetry()) {}
  fun visitTimeslotDeleted(event: AgencyVisitTimeEvent) = track("officialvisits-timeslot-synchronisation-deleted", event.asTelemetry()) {}
  fun visitSlotAdded(event: AgencyVisitSlotEvent) = track("officialvisits-visitslot-synchronisation-created", event.asTelemetry()) {}
  fun visitSlotUpdated(event: AgencyVisitSlotEvent) = track("officialvisits-visitslot-synchronisation-updated", event.asTelemetry()) {}
  fun visitSlotDeleted(event: AgencyVisitSlotEvent) = track("officialvisits-visitslot-synchronisation-deleted", event.asTelemetry()) {}
}

fun AgencyVisitTimeEvent.asTelemetry() = mutableMapOf<String, Any>(
  "prisonId" to agencyLocationId,
  "nomisWeekDay" to weekDay,
  "nomisTimeslotSequence" to timeslotSequence,
)

fun AgencyVisitSlotEvent.asTelemetry() = mutableMapOf<String, Any>(
  "prisonId" to agencyLocationId,
  "nomisWeekDay" to weekDay,
  "nomisTimeslotSequence" to timeslotSequence,
  "nomisAgencyVisitSlotId" to agencyVisitSlotId.toString(),
)
