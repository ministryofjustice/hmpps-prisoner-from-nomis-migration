package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ProfileDetailsChangedEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdatePrisonerDomesticStatusRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdatePrisonerNumberOfChildrenRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.synchronisationUser
import java.time.LocalDateTime

enum class ContactPersonProfileType(val telemetryName: String) {
  MARITAL("domestic-status"),
  CHILD("number-of-children"),
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

  suspend fun profileDetailsChanged(offenderNo: String, bookingId: Long? = null, profileType: ContactPersonProfileType) {
    val telemetry = telemetryOf("offenderNo" to offenderNo)
    bookingId?.run { telemetry["bookingId"] = bookingId.toString() }

    track("contact-person-${profileType.telemetryName}-synchronisation", telemetry) {
      val nomisResponse = nomisApi.getProfileDetails(offenderNo, listOf(profileType.name), bookingId)

      val booking = findBooking(bookingId, nomisResponse.bookings)
      bookingId ?: run { telemetry["bookingId"] = booking.bookingId.toString() }
      telemetry["latestBooking"] = booking.latestBooking

      val profileDetails = booking.profileDetails.find { it.type == profileType.name }
        ?: throw DomesticStatusChangedException("No ${profileType.name} profile type found for bookingId $bookingId")
      val (lastModifiedTime, lastModifiedBy) = profileDetails.lastModified()

      getIgnoreReason(nomisResponse.bookings.size, booking.latestBooking, bookingId, profileDetails)
        ?.let { ignoreReason ->
          telemetry["reason"] = ignoreReason
          telemetryClient.trackEvent("contact-person-${profileType.telemetryName}-synchronisation-ignored", telemetry)
          return
        }

      when (profileType) {
        ContactPersonProfileType.MARITAL -> {
          dpsApi.syncDomesticStatus(
            offenderNo,
            SyncUpdatePrisonerDomesticStatusRequest(lastModifiedBy, lastModifiedTime, profileDetails.code),
          ).id
        }
        ContactPersonProfileType.CHILD -> {
          dpsApi.syncNumberOfChildren(
            offenderNo,
            SyncUpdatePrisonerNumberOfChildrenRequest(lastModifiedBy, lastModifiedTime, profileDetails.code),
          ).id
        }
      }.also { telemetry["dpsId"] = it }
    }
  }

  private fun findBooking(
    bookingId: Long?,
    bookings: List<BookingProfileDetailsResponse>,
  ): BookingProfileDetailsResponse {
    val booking = if (bookingId != null) {
      bookings.find { it.bookingId == bookingId }
        ?: throw DomesticStatusChangedException("No booking found for bookingId $bookingId")
    } else {
      bookings.find { it.latestBooking }
        ?: throw DomesticStatusChangedException("Could not find latest booking")
    }
    return booking
  }

  private fun getIgnoreReason(
    bookingCount: Int,
    latestBooking: Boolean,
    bookingId: Long?,
    profileDetails: ProfileDetailsResponse,
  ): String? = if (bookingCount == 1 && profileDetails.code == null && profileDetails.modifiedDateTime == null) {
    "New profile details are empty"
  } else if (profileDetails.auditModuleName == synchronisationUser) {
    "Profile details were created by $synchronisationUser"
  } else if (!latestBooking) {
    "Ignoring historical bookingId $bookingId"
  } else {
    null
  }
  private fun ProfileDetailsResponse.lastModified(): Pair<LocalDateTime, String> = (modifiedDateTime ?: createDateTime).toLocalDateTime() to (modifiedBy ?: createdBy)

  private fun String.toLocalDateTime() = LocalDateTime.parse(this)
}

class DomesticStatusChangedException(message: String) : IllegalArgumentException(message)
