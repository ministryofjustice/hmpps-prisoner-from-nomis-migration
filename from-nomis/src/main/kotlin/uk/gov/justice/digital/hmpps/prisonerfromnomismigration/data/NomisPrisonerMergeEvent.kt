package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data

import java.time.OffsetDateTime

data class NomisPrisonerMergeEvent(
  val bookingId: Long,
)

data class PrisonerMergeDomainEvent(
  val occurredAt: OffsetDateTime,
  val additionalInformation: MergeAdditionalInformationEvent,
)

data class MergeAdditionalInformationEvent(
  val nomsNumber: String,
  val removedNomsNumber: String,
  val bookingId: Long,
)

data class PrisonerReceiveDomainEvent(
  val additionalInformation: ReceivePrisonerAdditionalInformationEvent,
)

data class ReceivePrisonerAdditionalInformationEvent(
  val nomsNumber: String,
  val reason: String,
)

data class PrisonerBookingMovedDomainEvent(
  val additionalInformation: BookingMovedAdditionalInformationEvent,
)

data class BookingMovedAdditionalInformationEvent(
  val movedToNomsNumber: String,
  val movedFromNomsNumber: String,
  val bookingId: Long,
)
