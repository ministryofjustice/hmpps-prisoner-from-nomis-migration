package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun LocalDateTime?.asStringOrBlank(): String = this?.format(DateTimeFormatter.ISO_DATE_TIME) ?: ""
fun LocalDate?.asStringOrBlank(): String = this?.format(DateTimeFormatter.ISO_DATE) ?: ""

class MigrationMessage<M, T>(
  val type: M,
  val context: MigrationContext<T>
)

enum class MigrationType(val queueId: String) {
  VISITS("migrationvisits"),
  INCENTIVES("migrationincentives")
}
