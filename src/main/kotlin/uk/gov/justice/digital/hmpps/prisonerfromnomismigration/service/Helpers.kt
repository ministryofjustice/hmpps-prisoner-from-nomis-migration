package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.SynchronisationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.reflect.full.memberProperties

fun LocalDateTime?.asStringOrBlank(): String = this?.format(DateTimeFormatter.ISO_DATE_TIME) ?: ""
fun LocalDate?.asStringOrBlank(): String = this?.format(DateTimeFormatter.ISO_DATE) ?: ""

open class LocalMessage<M>(
  val type: M,
)

class MigrationMessage<M, T>(
  type: M,
  val context: MigrationContext<T>,
) : LocalMessage<M>(type)

class SynchronisationMessage<M, T>(
  type: M,
  val context: SynchronisationContext<T>,
) : LocalMessage<M>(type)

const val VISITS_QUEUE_ID = "migrationvisits"
const val INCENTIVES_QUEUE_ID = "migrationincentives"
const val SENTENCING_ADJUSTMENTS_QUEUE_ID = "migrationsentencing"

const val VISITS_SYNC_QUEUE_ID = "eventvisits"
const val INCENTIVES_SYNC_QUEUE_ID = "eventincentives"
const val SENTENCING_ADJUSTMENTS_SYNC_QUEUE_ID = "eventsentencing"

enum class MigrationType(val queueId: String, val telemetryName: String) {
  VISITS(VISITS_QUEUE_ID, "visits"),
  INCENTIVES(INCENTIVES_QUEUE_ID, "incentives"),
  SENTENCING_ADJUSTMENTS(SENTENCING_ADJUSTMENTS_QUEUE_ID, "sentencing-adjustments"),
}

enum class SynchronisationType(val queueId: String, val telemetryName: String) {
  VISITS(VISITS_SYNC_QUEUE_ID, "visits"),
  INCENTIVES(INCENTIVES_SYNC_QUEUE_ID, "incentives"),
  SENTENCING_ADJUSTMENTS(SENTENCING_ADJUSTMENTS_SYNC_QUEUE_ID, "sentencing-adjustments"),
}

fun Any.asMap(): Map<String, String> {
  return this::class.memberProperties
    .filter { it.getter.call(this) != null }
    .associate { it.name to it.getter.call(this).toString() }
}
