package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MigrationContext<T>(val migrationId: String, val estimatedCount: Long, val body: T) {
  constructor(context: MigrationContext<*>, body: T) : this(context.migrationId, context.estimatedCount, body)
}

fun generateBatchId(): String = LocalDateTime.now().withNano(0).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
