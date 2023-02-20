package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.SynchronisationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun LocalDateTime?.asStringOrBlank(): String = this?.format(DateTimeFormatter.ISO_DATE_TIME) ?: ""
fun LocalDate?.asStringOrBlank(): String = this?.format(DateTimeFormatter.ISO_DATE) ?: ""

open class LocalMessage<M>(
  val type: M,
)

class MigrationMessage<M, T>(
  type: M,
  val context: MigrationContext<T>
) : LocalMessage<M>(type)

class SynchronisationMessage<M, T>(
  type: M,
  val context: SynchronisationContext<T>
) : LocalMessage<M>(type)

const val VISITS_QUEUE_ID = "migrationvisits"
const val INCENTIVES_QUEUE_ID = "migrationincentives"
const val SENTENCING_QUEUE_ID = "migrationsentencing"

enum class SynchronisationType(val queueId: String) {
  VISITS(VISITS_QUEUE_ID),
  INCENTIVES(INCENTIVES_QUEUE_ID),
  SENTENCING(SENTENCING_QUEUE_ID)
}
