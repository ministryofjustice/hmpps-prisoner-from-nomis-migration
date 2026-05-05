package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.DirectionCode

private const val TELEMETRY_PREFIX: String = "${CRT_TELEMETRY_PREFIX}-schedule"

@Service
class CourtSchedulerScheduleService(
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun courtScheduleInserted(event: CourtScheduleEvent) = when (event.directionCode) {
    DirectionCode.OUT -> syncCourtScheduleOutInserted(event)
    // TODO when direction is added to the event put this else back in - for now we'll have to check the direction after the nomis call
    //    else -> log.info("Ignoring insert of scheduled movement event ID ${event.eventId} with direction ${event.directionCode} ")
    else -> syncCourtScheduleOutInserted(event)
  }

  suspend fun syncCourtScheduleOutInserted(event: CourtScheduleEvent) {
    track("${TELEMETRY_PREFIX}-inserted", mutableMapOf()) {}
  }

  suspend fun courtScheduleUpdated(event: CourtScheduleEvent) = when (event.directionCode) {
    DirectionCode.OUT -> syncCourtScheduleOutUpdated(event)
    // TODO when direction is added to the event put this else back in - for now we'll have to check the direction after the nomis call
    //    else -> log.info("Ignoring update of scheduled movement event ID ${event.eventId} with direction ${event.directionCode} ")
    else -> syncCourtScheduleOutUpdated(event)
  }

  suspend fun syncCourtScheduleOutUpdated(event: CourtScheduleEvent) {
    track("${TELEMETRY_PREFIX}-udpated", mutableMapOf()) {}
  }

  suspend fun courtScheduleDeleted(event: CourtScheduleEvent) = when (event.directionCode) {
    DirectionCode.OUT -> syncCourtScheduleOutDeleted(event)
    // TODO when direction is added to the event put this else back in - for now we'll have to check the direction after the nomis call
    //    else -> log.info("Ignoring delete of scheduled movement event ID ${event.eventId} with direction ${event.directionCode} ")
    else -> syncCourtScheduleOutDeleted(event)
  }

  suspend fun syncCourtScheduleOutDeleted(event: CourtScheduleEvent) {
    track("${TELEMETRY_PREFIX}-deleted", mutableMapOf()) {}
  }
}
