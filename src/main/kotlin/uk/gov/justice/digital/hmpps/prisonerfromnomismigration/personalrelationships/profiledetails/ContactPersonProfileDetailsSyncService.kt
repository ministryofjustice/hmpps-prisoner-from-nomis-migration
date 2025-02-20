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

enum class ContactPersonProfileType(val telemetryName: String) {
  MARITAL("domestic-status"),
  CHILD("dependants"),
}

@Service
class ContactPersonProfileDetailsSyncService(
  private val nomisApi: ContactPersonProfileDetailsNomisApiService,
  private val dpsApi: ContactPersonProfileDetailsDpsApiService,
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {

  suspend fun profileDetailsChanged(event: ProfileDetailsChangedEvent) = with(event) {
    runCatching { ContactPersonProfileType.valueOf(profileType) }
      .map {
        profileDetailsChanged(offenderIdDisplay, bookingId, it)
      }
  }

  suspend fun profileDetailsChanged(offenderNo: String, bookingId: Long, profileType: ContactPersonProfileType) {
    val telemetry = telemetryOf(
      "offenderNo" to offenderNo,
      "bookingId" to bookingId,
    )

    track("contact-person-${profileType.telemetryName}-synchronisation", telemetry) {
      val nomisResponse = nomisApi.getProfileDetails(offenderNo)
      val booking = nomisResponse.bookings.find { it.bookingId == bookingId }
        ?: throw DomesticStatusChangedException("No booking found for bookingId $bookingId")
      telemetry["latestBooking"] = booking.latestBooking
      val profileDetails = booking.profileDetails.find { it.type == profileType.name }
        ?: throw DomesticStatusChangedException("No ${profileType.name} profile type found for bookingId $bookingId")
      val (lastModifiedTime, lastModifiedBy) = profileDetails.lastModified()

      getIgnoreReason(nomisResponse.bookings.size, profileDetails)
        ?.let { ignoreReason ->
          telemetry["reason"] = ignoreReason
          telemetryClient.trackEvent("contact-person-${profileType.telemetryName}-synchronisation-ignored", telemetry)
          return
        }

      when (profileType) {
        ContactPersonProfileType.MARITAL -> {
          dpsApi.syncDomesticStatus(
            offenderNo,
            DomesticStatusSyncRequest(profileDetails.code, lastModifiedBy, lastModifiedTime, booking.latestBooking),
          ).domesticStatusId
        }
        ContactPersonProfileType.CHILD -> {
          dpsApi.syncDependants(
            offenderNo,
            DependantsSyncRequest(profileDetails.code, lastModifiedBy, lastModifiedTime, booking.latestBooking),
          ).dependantsId
        }
      }.also { telemetry["dpsId"] = it }
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
