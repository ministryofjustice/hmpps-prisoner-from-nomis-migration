package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import java.time.LocalDateTime

data class HistoryFilter(
  val migrationTypes: List<String>? = null,

  val fromDateTime: LocalDateTime? = null,

  val toDateTime: LocalDateTime? = null,

  val includeOnlyFailures: Boolean = false,

  val filterContains: String? = null,
)
