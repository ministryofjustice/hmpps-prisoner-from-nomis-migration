package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
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

const val VISITS_QUEUE_ID = "migrationvisits"
const val APPOINTMENTS_QUEUE_ID = "migrationappointments"
const val ACTIVITIES_QUEUE_ID = "migrationactivities"
const val ALLOCATIONS_QUEUE_ID = "migrationallocations"
const val INCIDENTS_QUEUE_ID = "migrationincidents"
const val CORE_PERSON_QUEUE_ID = "migrationcoreperson"
const val COURT_SENTENCING_QUEUE_ID = "migrationcourtsentencing"
const val PERSONALRELATIONSHIPS_QUEUE_ID = "migrationpersonalrelationships"
const val PERSONALRELATIONSHIPS_PROFILEDETAILS_QUEUE_ID = "migrationpersonalrelationshipsprofiledetails"
const val ORGANISATIONS_QUEUE_ID = "migrationorganisations"
const val SENTENCING_ADJUSTMENTS_QUEUE_ID = "migrationsentencing"
const val VISIT_BALANCE_QUEUE_ID = "migrationvisitbalance"
const val EXTERNAL_MOVEMENTS_QUEUE_ID = "migrationexternalmovements"

const val VISITS_SYNC_QUEUE_ID = "eventvisits"
const val SENTENCING_ADJUSTMENTS_SYNC_QUEUE_ID = "eventsentencing"
const val COURT_SENTENCING_SYNC_QUEUE_ID = "eventcourtsentencing"
const val INCIDENTS_SYNC_QUEUE_ID = "eventincidents"
const val LOCATIONS_SYNC_QUEUE_ID = "eventlocations"
const val CASENOTES_SYNC_QUEUE_ID = "eventcasenotes"
const val ALERTS_SYNC_QUEUE_ID = "eventalerts"
const val PERSONALRELATIONSHIPS_SYNC_QUEUE_ID = "eventpersonalrelationships"
const val PERSONALRELATIONSHIPS_DOMAIN_SYNC_QUEUE_ID = "domaineventpersonalrelationships"
const val PERSONCONTACTS_DOMAIN_SYNC_QUEUE_ID = "domaineventpersoncontacts"
const val PRISONERRESTRICTIONS_DOMAIN_SYNC_QUEUE_ID = "domaineventprisonerrestrictions"
const val ORGANISATIONS_SYNC_QUEUE_ID = "eventorganisations"
const val FINANCE_SYNC_QUEUE_ID = "eventfinance"
const val VISIT_BALANCE_SYNC_QUEUE_ID = "eventvisitbalance"

enum class MigrationType(val queueId: String, val telemetryName: String) {
  VISITS(VISITS_QUEUE_ID, "visits"),
  APPOINTMENTS(APPOINTMENTS_QUEUE_ID, "appointments"),
  ACTIVITIES(ACTIVITIES_QUEUE_ID, "activity"),
  ALLOCATIONS(ALLOCATIONS_QUEUE_ID, "activity-allocation"),
  CORE_PERSON(CORE_PERSON_QUEUE_ID, "coreperson"),
  INCIDENTS(INCIDENTS_QUEUE_ID, "incidents"),
  COURT_SENTENCING(COURT_SENTENCING_QUEUE_ID, "court-sentencing"),
  PERSONALRELATIONSHIPS(PERSONALRELATIONSHIPS_QUEUE_ID, "contactperson"),
  PERSONALRELATIONSHIPS_PROFILEDETAIL(PERSONALRELATIONSHIPS_PROFILEDETAILS_QUEUE_ID, "contactperson-profiledetails"),
  ORGANISATIONS(ORGANISATIONS_QUEUE_ID, "corporate"),
  SENTENCING_ADJUSTMENTS(SENTENCING_ADJUSTMENTS_QUEUE_ID, "sentencing-adjustments"),
  VISIT_BALANCE(VISIT_BALANCE_QUEUE_ID, "visitbalance"),
  EXTERNAL_MOVEMENTS(EXTERNAL_MOVEMENTS_QUEUE_ID, "temporary-absences"),
}

enum class SynchronisationType(val queueId: String) {
  VISITS(VISITS_SYNC_QUEUE_ID),
  SENTENCING_ADJUSTMENTS(SENTENCING_ADJUSTMENTS_SYNC_QUEUE_ID),
  INCIDENTS(INCIDENTS_SYNC_QUEUE_ID),
  ALERTS(ALERTS_SYNC_QUEUE_ID),
  LOCATIONS(LOCATIONS_SYNC_QUEUE_ID),
  CASENOTES(CASENOTES_SYNC_QUEUE_ID),
  COURT_SENTENCING(COURT_SENTENCING_SYNC_QUEUE_ID),
  PERSONALRELATIONSHIPS(PERSONALRELATIONSHIPS_SYNC_QUEUE_ID),
  ORGANISATIONS(ORGANISATIONS_SYNC_QUEUE_ID),
  FINANCE(FINANCE_SYNC_QUEUE_ID),
  VISIT_BALANCE(VISIT_BALANCE_SYNC_QUEUE_ID),
}

fun Any.asMap(): Map<String, String> = this::class.memberProperties
  .filter { it.getter.call(this) != null }
  .associate { it.name to it.getter.call(this).toString() }
