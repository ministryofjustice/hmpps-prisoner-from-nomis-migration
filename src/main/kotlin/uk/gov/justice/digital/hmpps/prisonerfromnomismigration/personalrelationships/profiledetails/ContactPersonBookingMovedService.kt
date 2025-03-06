package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerBookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ProfileDetailsResponse

@Service
class ContactPersonBookingMovedService(
  private val nomisApiService: ContactPersonProfileDetailsNomisApiService,
  private val nomisSyncApiService: ContactPersonNomisSyncApiService,
  private val syncService: ContactPersonProfileDetailsSyncService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun bookingMoved(event: PrisonerBookingMovedDomainEvent) {
    val bookingId = event.additionalInformation.bookingId
    val toOffenderNo = event.additionalInformation.movedToNomsNumber
    val fromOffenderNo = event.additionalInformation.movedFromNomsNumber
    val telemetry = mutableMapOf(
      "bookingId" to bookingId.toString(),
      "toOffenderNo" to toOffenderNo,
      "fromOffenderNo" to fromOffenderNo,
    )

    syncOffenderToDps(fromOffenderNo, telemetry)
    syncOffenderToDps(toOffenderNo, telemetry, mustHaveChanged = true)
    syncOffenderToNomis(toOffenderNo, telemetry)
    telemetryClient.trackEvent("contact-person-booking-moved", telemetry, null)
  }

  private fun MutableMap<String, String>.addToTelemetry(telemetryKey: String, addProfileType: String) {
    val profileTypes = this[telemetryKey]
    val newProfileTypes = profileTypes?.split(",")?.plus(addProfileType) ?: listOf(addProfileType)
    this[telemetryKey] = newProfileTypes.joinToString(",")
  }

  suspend fun syncOffenderToDps(
    offenderNo: String,
    telemetry: MutableMap<String, String>,
    mustHaveChanged: Boolean = false,
  ) {
    nomisApiService.getProfileDetails(offenderNo, ContactPersonProfileType.all()).also { nomisResponse ->
      nomisResponse.bookings.firstOrNull { it.latestBooking }
        ?.also { booking ->
          booking.profileDetails.forEach { profileDetail ->
            if (!mustHaveChanged || (profileDetail.lastModifiedDateTime() > booking.startDateTime)) {
              val profileType = ContactPersonProfileType.valueOf(profileDetail.type)
              syncService.profileDetailsChanged(
                offenderNo = offenderNo,
                profileType = ContactPersonProfileType.valueOf(profileDetail.type),
                nomisResponse = nomisResponse,
              )
                .also { telemetry.addToTelemetry("syncToDps", "$offenderNo-$profileType") }
            }
          }
        }
    }
  }

  suspend fun syncOffenderToNomis(offenderNo: String, telemetry: MutableMap<String, String>) {
    ContactPersonProfileType.all().forEach { profileType ->
      nomisSyncApiService.syncProfileDetails(offenderNo, profileType)
        .also { telemetry.addToTelemetry("syncToNomis", "$offenderNo-$profileType") }
    }
  }
}

internal fun ProfileDetailsResponse.lastModifiedDateTime() = modifiedDateTime ?: createDateTime
