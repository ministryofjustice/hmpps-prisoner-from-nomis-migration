package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.doesOriginateInDps
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent

@Service
class PrisonerRestrictionSynchronisationService(
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {
  suspend fun prisonerRestrictionUpserted(event: PrisonerRestrictionEvent) = when (event.isUpdated) {
    true -> prisonerRestrictionUpdated(event)
    false -> prisonerRestrictionCreated(event)
  }
  suspend fun prisonerRestrictionCreated(event: PrisonerRestrictionEvent) {
    val telemetry = telemetryOf(
      "offenderNo" to event.offenderIdDisplay,
      "nomisRestrictionId" to event.offenderRestrictionId,
    )

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-prisoner-restriction-synchronisation-created-skipped",
        telemetry,
      )
    } else {
      track("contactperson-prisoner-restriction-synchronisation-created", telemetry) {
        // TODO - sync
      }
    }
  }
  suspend fun prisonerRestrictionUpdated(event: PrisonerRestrictionEvent) {
    val telemetry = telemetryOf(
      "offenderNo" to event.offenderIdDisplay,
      "nomisRestrictionId" to event.offenderRestrictionId,
    )
    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-prisoner-restriction-synchronisation-updated-skipped",
        telemetry,
      )
    } else {
      track("contactperson-prisoner-restriction-synchronisation-updated", telemetry) {
        // TODO - sync
      }
    }
  }
  suspend fun prisonerRestrictionDeleted(event: PrisonerRestrictionEvent) {
    val telemetry = telemetryOf(
      "offenderNo" to event.offenderIdDisplay,
      "nomisRestrictionId" to event.offenderRestrictionId,
    )
    track("contactperson-prisoner-restriction-synchronisation-deleted", telemetry) {
      // TODO - sync
    }
  }
}
