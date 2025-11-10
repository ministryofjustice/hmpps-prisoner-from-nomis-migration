package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Deferred
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited.Companion.DEFAULT_AUDIT_MODULE

suspend fun <A, B> Pair<Deferred<A>, Deferred<B>>.awaitBoth(): Pair<A, B> = this.first.await() to this.second.await()

fun Long.asPages(pageSize: Long): Array<Pair<Long, Long>> = (0..(this / pageSize)).map { it to pageSize }.toTypedArray()

interface EventAudited {
  val auditModuleName: String?

  companion object {
    const val DEFAULT_AUDIT_MODULE = "DPS_SYNCHRONISATION"
  }
}

// Calls to this method should cater for the dodgy Nomis data
fun EventAudited.originatesInDps(): Boolean = auditModuleName.orEmpty().startsWith(DEFAULT_AUDIT_MODULE)

// Caters for null or empty which should be treated as DPS_SYNCHRONISATION
fun EventAudited.originatesInDpsOrHasMissingAudit(): Boolean = auditModuleName.isNullOrEmpty() || auditModuleName?.startsWith(DEFAULT_AUDIT_MODULE) == true
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
