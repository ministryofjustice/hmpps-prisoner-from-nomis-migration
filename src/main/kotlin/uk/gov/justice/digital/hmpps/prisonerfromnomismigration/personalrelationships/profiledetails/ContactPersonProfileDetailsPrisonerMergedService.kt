package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerMergeDomainEvent

@Service
class ContactPersonProfileDetailsPrisonerMergedService(
  private val dpsApiService: ContactPersonProfileDetailsDpsApiService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun prisonerMerged(event: PrisonerMergeDomainEvent) {
    val bookingId = event.additionalInformation.bookingId
    val keepingPrisonerNumber = event.additionalInformation.nomsNumber
    val removedPrisonerNumber = event.additionalInformation.removedNomsNumber
    val telemetry = mutableMapOf(
      "bookingId" to bookingId.toString(),
      "offenderNo" to keepingPrisonerNumber,
      "removedOffenderNo" to removedPrisonerNumber,
    )

    runCatching {
      dpsApiService.mergeProfileDetails(keepingPrisonerNumber, removedPrisonerNumber)
      telemetryClient.trackEvent("contact-person-profile-details-prisoner-merged", telemetry, null)
    }.onFailure { e ->
      telemetry["error"] = e.message.toString()
      telemetryClient.trackEvent("contact-person-profile-details-prisoner-merged-error", telemetry.toMap())
      throw e
    }
  }
}
