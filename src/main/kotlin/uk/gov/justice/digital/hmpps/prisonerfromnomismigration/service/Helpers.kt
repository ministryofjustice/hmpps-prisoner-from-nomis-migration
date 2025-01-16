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
const val APPOINTMENTS_QUEUE_ID = "migrationappointments"
const val ACTIVITIES_QUEUE_ID = "migrationactivities"
const val ALLOCATIONS_QUEUE_ID = "migrationallocations"
const val INCIDENTS_QUEUE_ID = "migrationincidents"
const val CSIP_QUEUE_ID = "migrationcsip"
const val LOCATIONS_QUEUE_ID = "migrationlocations"
const val CASENOTES_QUEUE_ID = "migrationcasenotes"
const val PRISONPERSON_QUEUE_ID = "migrationprisonperson"
const val COURT_SENTENCING_QUEUE_ID = "migrationcourtsentencing"
const val CONTACTPERSON_QUEUE_ID = "migrationcontactperson"
const val CORPORATE_QUEUE_ID = "migrationcorporate"
const val SENTENCING_ADJUSTMENTS_QUEUE_ID = "migrationsentencing"

const val VISITS_SYNC_QUEUE_ID = "eventvisits"
const val SENTENCING_ADJUSTMENTS_SYNC_QUEUE_ID = "eventsentencing"
const val COURT_SENTENCING_SYNC_QUEUE_ID = "eventcourtsentencing"
const val INCIDENTS_SYNC_QUEUE_ID = "eventincidents"
const val CSIP_SYNC_QUEUE_ID = "eventcsip"
const val LOCATIONS_SYNC_QUEUE_ID = "eventlocations"
const val CASENOTES_SYNC_QUEUE_ID = "eventcasenotes"
const val ALERTS_SYNC_QUEUE_ID = "eventalerts"
const val CONTACTPERSON_SYNC_QUEUE_ID = "eventcontactperson"

enum class MigrationType(val queueId: String, val telemetryName: String) {
  VISITS(VISITS_QUEUE_ID, "visits"),
  APPOINTMENTS(APPOINTMENTS_QUEUE_ID, "appointments"),
  ACTIVITIES(ACTIVITIES_QUEUE_ID, "activity"),
  ALLOCATIONS(ALLOCATIONS_QUEUE_ID, "activity-allocation"),
  INCIDENTS(INCIDENTS_QUEUE_ID, "incidents"),
  CSIP(CSIP_QUEUE_ID, "csip"),
  LOCATIONS(LOCATIONS_QUEUE_ID, "locations"),
  CASENOTES(CASENOTES_QUEUE_ID, "casenotes"),
  PRISONPERSON(PRISONPERSON_QUEUE_ID, "prisonperson"),
  COURT_SENTENCING(COURT_SENTENCING_QUEUE_ID, "court-sentencing"),
  CONTACTPERSON(CONTACTPERSON_QUEUE_ID, "contactperson"),
  CORPORATE(CORPORATE_QUEUE_ID, "corporate"),
  SENTENCING_ADJUSTMENTS(SENTENCING_ADJUSTMENTS_QUEUE_ID, "sentencing-adjustments"),
}

enum class SynchronisationType(val queueId: String) {
  VISITS(VISITS_SYNC_QUEUE_ID),
  SENTENCING_ADJUSTMENTS(SENTENCING_ADJUSTMENTS_SYNC_QUEUE_ID),
  INCIDENTS(INCIDENTS_SYNC_QUEUE_ID),
  CSIP(CSIP_SYNC_QUEUE_ID),
  ALERTS(ALERTS_SYNC_QUEUE_ID),
  LOCATIONS(LOCATIONS_SYNC_QUEUE_ID),
  CASENOTES(CASENOTES_SYNC_QUEUE_ID),
  COURT_SENTENCING(COURT_SENTENCING_SYNC_QUEUE_ID),
  CONTACTPERSON(CONTACTPERSON_SYNC_QUEUE_ID),
}

fun Any.asMap(): Map<String, String> = this::class.memberProperties
  .filter { it.getter.call(this) != null }
  .associate { it.name to it.getter.call(this).toString() }
