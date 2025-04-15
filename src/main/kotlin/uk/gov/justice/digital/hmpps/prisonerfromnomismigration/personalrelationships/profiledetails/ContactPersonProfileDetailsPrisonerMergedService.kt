package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerMergeDomainEvent

@Service
class ContactPersonProfileDetailsPrisonerMergedService(
  private val dpsApiService: ContactPersonProfileDetailsDpsApiService,
  private val nomisSyncApiService: ContactPersonProfileDetailsNomisSyncApiService,
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
      syncOffenderToNomis(keepingPrisonerNumber, telemetry)
      telemetryClient.trackEvent("contact-person-profile-details-prisoner-merged", telemetry, null)
    }.onFailure { e ->
      telemetry["error"] = e.message.toString()
      telemetryClient.trackEvent("contact-person-profile-details-prisoner-merged-error", telemetry.toMap())
      throw e
    }
  }

  suspend fun syncOffenderToNomis(offenderNo: String, telemetry: MutableMap<String, String>) {
    ContactPersonProfileType.all().forEach { profileType ->
      nomisSyncApiService.syncProfileDetails(offenderNo, profileType)
        .also { telemetry.addToTelemetry("syncToNomis", "$offenderNo-$profileType") }
    }
  }

  private fun MutableMap<String, String>.addToTelemetry(telemetryKey: String, addProfileType: String) {
    val profileTypes = this[telemetryKey]
    val newProfileTypes = profileTypes?.split(",")?.plus(addProfileType) ?: listOf(addProfileType)
    this[telemetryKey] = newProfileTypes.joinToString(",")
  }
}
