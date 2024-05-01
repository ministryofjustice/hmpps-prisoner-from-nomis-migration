package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data

data class NomisPrisonerMergeEvent(
  val bookingId: Long,
)

data class PrisonerMergeDomainEvent(
  val additionalInformation: MergeAdditionalInformationEvent,
)

data class MergeAdditionalInformationEvent(
  val nomsNumber: String,
  val removedNomsNumber: String,
  val bookingId: Long,
)
