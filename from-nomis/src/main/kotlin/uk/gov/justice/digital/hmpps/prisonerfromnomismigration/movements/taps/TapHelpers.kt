package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import java.time.LocalDate

const val TAP_TELEMETRY_PREFIX: String = "temporary-absence-sync"
const val DEFAULT_ESCORT_CODE = "NOT_PROVIDED"
const val DEFAULT_TRANSPORT_TYPE = "TNR"

fun String.toDpsAuthorisationStatusCode(toDate: LocalDate, latestBooking: Boolean) = when {
  !latestBooking && toDate.notEnded() && this.isApproved() -> "EXPIRED"
  this == "PEN" -> "PENDING"
  this.isApproved() -> "APPROVED"
  this == "DEN" -> "DENIED"
  this == "CANC" -> "CANCELLED"
  else -> throw IllegalArgumentException("Unknown temporary absence status code: $this")
}

private fun LocalDate.notEnded() = isAfter(LocalDate.now().minusDays(1))
private fun String.isApproved() = this in listOf("APP-SCH", "APP-UNSCH")
