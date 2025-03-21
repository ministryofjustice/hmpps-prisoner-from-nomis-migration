package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Deferred

suspend fun <A, B> Pair<Deferred<A>, Deferred<B>>.awaitBoth(): Pair<A, B> = this.first.await() to this.second.await()

fun Long.asPages(pageSize: Long): Array<Pair<Long, Long>> = (0..(this / pageSize)).map { it to pageSize }.toTypedArray()

interface EventAudited {
  val auditModuleName: String
}

fun EventAudited.doesOriginateInDps() = this.auditModuleName == "DPS_SYNCHRONISATION"

interface TelemetryEnabled {
  val telemetryClient: TelemetryClient
}

inline fun TelemetryEnabled.track(name: String, telemetry: MutableMap<String, Any>, transform: () -> Unit) {
  try {
    transform()
    telemetryClient.trackEvent("$name-success", telemetry)
  } catch (e: Exception) {
    telemetry["error"] = e.message.toString()
    telemetryClient.trackEvent("$name-error", telemetry)
    throw e
  }
}

enum class WhichMoveBookingPrisoner {
  FROM,
  TO,
}
data class MoveBookingForPrisoner(
  val bookingId: Long,
  val offenderNo: String,
  val whichPrisoner: WhichMoveBookingPrisoner,
)
