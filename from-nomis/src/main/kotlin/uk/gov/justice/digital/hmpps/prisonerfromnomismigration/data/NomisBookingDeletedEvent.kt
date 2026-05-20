package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data

data class NomisBookingDeletedEvent(
  val bookingId: Long,
  val offenderIdDisplay: String,
)
