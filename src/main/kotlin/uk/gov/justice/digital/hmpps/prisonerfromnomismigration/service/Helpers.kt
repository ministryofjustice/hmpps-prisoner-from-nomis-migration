package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.SynchronisationContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.reflect.full.memberProperties

fun LocalDateTime?.asStringOrBlank(): String = this?.format(DateTimeFormatter.ISO_DATE_TIME) ?: ""

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
const val SENTENCING_ADJUSTMENTS_QUEUE_ID = "migrationsentencing"
const val APPOINTMENTS_QUEUE_ID = "migrationappointments"
const val ADJUDICATIONS_QUEUE_ID = "migrationadjudications"
const val ACTIVITIES_QUEUE_ID = "migrationactivities"
const val ALLOCATIONS_QUEUE_ID = "migrationallocations"
const val INCIDENTS_QUEUE_ID = "migrationincidents"
const val NON_ASSOCIATIONS_QUEUE_ID = "migrationnonassociations"

const val VISITS_SYNC_QUEUE_ID = "eventvisits"
const val SENTENCING_ADJUSTMENTS_SYNC_QUEUE_ID = "eventsentencing"
const val NON_ASSOCIATIONS_SYNC_QUEUE_ID = "eventnonassociations"
const val INCIDENTS_SYNC_QUEUE_ID = "eventincidents"

enum class MigrationType(val queueId: String, val telemetryName: String) {
  VISITS(VISITS_QUEUE_ID, "visits"),
  SENTENCING_ADJUSTMENTS(SENTENCING_ADJUSTMENTS_QUEUE_ID, "sentencing-adjustments"),
  APPOINTMENTS(APPOINTMENTS_QUEUE_ID, "appointments"),
  ADJUDICATIONS(ADJUDICATIONS_QUEUE_ID, "adjudications"),
  ACTIVITIES(ACTIVITIES_QUEUE_ID, "activity"),
  ALLOCATIONS(ALLOCATIONS_QUEUE_ID, "activity-allocation"),
  INCIDENTS(INCIDENTS_QUEUE_ID, "incidents"),
  NON_ASSOCIATIONS(NON_ASSOCIATIONS_QUEUE_ID, "non-associations"),
}

enum class SynchronisationType(val queueId: String) {
  VISITS(VISITS_SYNC_QUEUE_ID),
  SENTENCING_ADJUSTMENTS(SENTENCING_ADJUSTMENTS_SYNC_QUEUE_ID),
  NON_ASSOCIATIONS(NON_ASSOCIATIONS_SYNC_QUEUE_ID),
  INCIDENTS(INCIDENTS_SYNC_QUEUE_ID),
}

fun Any.asMap(): Map<String, String> {
  return this::class.memberProperties
    .filter { it.getter.call(this) != null }
    .associate { it.name to it.getter.call(this).toString() }
}
