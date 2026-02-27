package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Deferred
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited.Companion.DPS_SYNC_AUDIT_MODULE

suspend fun <A, B> Pair<Deferred<A>, Deferred<B>>.awaitBoth(): Pair<A, B> = this.first.await() to this.second.await()

fun Long.asPages(pageSize: Long): Array<Pair<Long, Long>> = (0..(this / pageSize)).map { it to pageSize }.toTypedArray()

fun String?.originatesInDps() = orEmpty().startsWith(DPS_SYNC_AUDIT_MODULE)

interface EventAudited {
  val auditModuleName: String?

  companion object {
    const val DPS_SYNC_AUDIT_MODULE = "DPS_SYNCHRONISATION"
    const val FLUSH_SCHEDULES_AUDIT_MODULE = "FLUSH_SCHEDULES"
  }

  val originatesInDps: Boolean
    get() = auditModuleName.originatesInDps()

  val triggeredByFlushSchedules: Boolean
    get() = FLUSH_SCHEDULES_AUDIT_MODULE == auditModuleName

  // Caters for null or empty which should be treated as DPS_SYNCHRONISATION
  val originatesInDpsOrHasMissingAudit: Boolean
    get() = auditModuleName.isNullOrEmpty() || auditModuleName?.startsWith(DPS_SYNC_AUDIT_MODULE) == true

  fun auditExactMatchOrHasMissingAudit(audit: String) = auditModuleName.isNullOrEmpty() || auditModuleName == audit
}

interface TelemetryEnabled {
  val telemetryClient: TelemetryClient
}

inline fun TelemetryEnabled.track(name: String, telemetry: MutableMap<String, Any>, transform: () -> Unit) {
  try {
    transform()
    telemetryClient.trackEvent("$name-success", telemetry)
  } catch (e: AwaitParentEntityRetry) {
    telemetry["error"] = e.message.toString()
    telemetryClient.trackEvent("$name-awaiting-parent", telemetry)
    throw e
  } catch (e: Exception) {
    telemetry["error"] = e.message.toString()
    telemetryClient.trackEvent("$name-error", telemetry)
    throw e
  }
}

inline fun <T> TelemetryEnabled.trackIfFailure(name: String, telemetry: MutableMap<String, String>, transform: () -> T): T = try {
  transform()
} catch (e: Exception) {
  telemetry["error"] = e.message.toString()
  telemetryClient.trackEvent("$name-error", telemetry)
  throw e
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

class AwaitParentEntityRetry(message: String) : ParentEntityNotFoundRetry(message)

suspend fun <T> tryFetchParent(get: suspend () -> T?): T = get() ?: throw AwaitParentEntityRetry(
  "Expected parent entity not found, retrying",
)
