package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MigrationContext<T>(
  val type: MigrationType,
  val migrationId: String,
  val estimatedCount: Long,
  val body: T
) {
  constructor(context: MigrationContext<*>, body: T) : this(
    context.type,
    context.migrationId,
    context.estimatedCount,
    body
  )
}

fun generateBatchId(): String = LocalDateTime.now().withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

class SynchronisationContext<T>(
  val type: MigrationType,
  val telemetryProperties: Map<String, String> = emptyMap(),
  val body: T
)
