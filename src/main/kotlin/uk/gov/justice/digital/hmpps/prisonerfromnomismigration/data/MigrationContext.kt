package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MigrationContext<T>(val batchId: String, val filter: T)

fun generateBatchId(): String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
