package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ScheduledMovementEvent

private const val TELEMETRY_PREFIX: String = "${TRANSFER_TELEMETRY_PREFIX}-schedule"

@Service
class TransferScheduleSyncScheduleService(
  private val telemetryClient: TelemetryClient,
) {

  suspend fun syncTransferScheduleOutInserted(event: ScheduledMovementEvent) = telemetryClient.trackEvent(
    "$TELEMETRY_PREFIX-inserted-success",
    mapOf(),
  )
}
