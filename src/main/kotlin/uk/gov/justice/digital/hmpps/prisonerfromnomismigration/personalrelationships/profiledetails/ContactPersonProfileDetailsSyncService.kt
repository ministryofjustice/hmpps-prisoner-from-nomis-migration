package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ProfileDetailsChangedEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.synchronisationUser
import java.time.LocalDateTime

@Service
class ContactPersonProfileDetailsSyncService(
  private val nomisApi: ContactPersonProfileDetailsNomisApiService,
  private val dpsApi: ContactPersonProfileDetailsDpsApiService,
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {

  suspend fun profileDetailsChanged(event: ProfileDetailsChangedEvent) = with(event) {
    profileDetailsChanged(profileType, offenderIdDisplay, bookingId)
  }

  suspend fun profileDetailsChanged(profileType: String, offenderNo: String, bookingId: Long) {
    when (profileType) {
      "MARITAL" -> domesticStatusChanged(offenderNo, bookingId)
    }
  }

  suspend fun domesticStatusChanged(offenderNo: String, bookingId: Long) {
    val telemetry = telemetryOf(
      "offenderNo" to offenderNo,
      "bookingId" to bookingId,
    )

    track("contact-person-domestic-status-synchronisation", telemetry) {
      val nomisResponse = nomisApi.getProfileDetails(offenderNo)
      val booking = nomisResponse.bookings.find { it.bookingId == bookingId }
        ?: throw DomesticStatusChangedException("No booking found for bookingId $bookingId")
      telemetry["latestBooking"] = booking.latestBooking
      val profileDetails = booking.profileDetails.find { it.type == "MARITAL" }
        ?: throw DomesticStatusChangedException("No MARITAL profile type found for bookingId $bookingId")
      val (lastModifiedTime, lastModifiedBy) = profileDetails.lastModified()

      getIgnoreReason(nomisResponse.bookings.size, profileDetails)
        ?.let { ignoreReason ->
          telemetry["reason"] = ignoreReason
          telemetryClient.trackEvent("contact-person-domestic-status-synchronisation-ignored", telemetry)
          return
        }

      dpsApi.syncDomesticStatus(
        offenderNo,
        DomesticStatusSyncRequest(profileDetails.code, lastModifiedBy, lastModifiedTime, booking.latestBooking),
      ).also { dpsResponse -> telemetry["dpsId"] = dpsResponse.domesticStatusId }
    }
  }

  private fun getIgnoreReason(
    bookingCount: Int,
    profileDetails: ProfileDetailsResponse,
  ): String? = if (bookingCount == 1 && profileDetails.code == null && profileDetails.modifiedDateTime == null) {
    "New profile details are empty"
  } else if (profileDetails.auditModuleName == synchronisationUser) {
    "Profile details were created by $synchronisationUser"
  } else {
    null
  }
  private fun ProfileDetailsResponse.lastModified(): Pair<LocalDateTime, String> = (modifiedDateTime ?: createDateTime).toLocalDateTime() to (modifiedBy ?: createdBy)

  private fun String.toLocalDateTime() = LocalDateTime.parse(this)
}

class DomesticStatusChangedException(message: String) : IllegalArgumentException(message)
