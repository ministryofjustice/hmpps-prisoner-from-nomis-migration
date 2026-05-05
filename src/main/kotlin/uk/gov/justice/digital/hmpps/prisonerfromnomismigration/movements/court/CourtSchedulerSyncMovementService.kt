package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.MovementType.CRT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.ExternalMovementEvent

private const val TELEMETRY_PREFIX: String = "${CRT_TELEMETRY_PREFIX}-movement"

@Service
class CourtSchedulerSyncMovementService(
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun courtMovementChanged(event: ExternalMovementEvent) = when {
    event.movementType != CRT -> {}
    event.recordInserted -> courtMovementInserted(event)
    event.recordDeleted -> courtMovementDeleted(event)
    else -> courtMovementUpdated(event)
  }

  suspend fun courtMovementInserted(event: ExternalMovementEvent) {
    track("${TELEMETRY_PREFIX}-inserted", mutableMapOf()) {}
  }

  suspend fun courtMovementUpdated(event: ExternalMovementEvent) {
    track("${TELEMETRY_PREFIX}-updated", mutableMapOf()) {}
  }

  suspend fun courtMovementDeleted(event: ExternalMovementEvent) {
    track("${TELEMETRY_PREFIX}-deleted", mutableMapOf()) {}
  }
}
