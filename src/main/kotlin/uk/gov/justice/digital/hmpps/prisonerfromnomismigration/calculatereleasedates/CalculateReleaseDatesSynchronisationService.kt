package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent

private const val TELEMETRY_PREFIX = "calculatereleasedates-synchronisation-sentence-calculation"

/**
 * Skeleton - only tracks telemetry until the synchronisation is implemented.
 */
@Service
class CalculateReleaseDatesSynchronisationService(
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {

  suspend fun sentenceCalculationInserted(event: SentenceCalculationEvent) = telemetryClient.trackEvent("$TELEMETRY_PREFIX-created", event.toTelemetry())

  suspend fun sentenceCalculationUpdated(event: SentenceCalculationEvent) = telemetryClient.trackEvent("$TELEMETRY_PREFIX-updated", event.toTelemetry())

  suspend fun sentenceCalculationDeleted(event: SentenceCalculationEvent) = telemetryClient.trackEvent("$TELEMETRY_PREFIX-deleted", event.toTelemetry())

  private fun SentenceCalculationEvent.toTelemetry() = telemetryOf(
    "offenderNo" to offenderIdDisplay,
    "bookingId" to bookingId,
    "nomisCalculationId" to offenderSentCalculationId,
  )
}
