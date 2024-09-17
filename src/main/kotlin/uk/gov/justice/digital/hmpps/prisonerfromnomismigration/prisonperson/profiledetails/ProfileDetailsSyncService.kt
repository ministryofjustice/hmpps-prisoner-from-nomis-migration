package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.ProfileDetailsChangedEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.synchronisationUser
import java.time.LocalDateTime

@Service
class ProfileDetailsSyncService(
  private val nomisApiService: ProfileDetailsNomisApiService,
  private val dpsApiService: ProfileDetailPhysicalAttributesDpsApiService,
  private val telemetryClient: TelemetryClient,
) {

  private fun String.isPhysicalAttributesProfileType() =
    listOf("BUILD", "L_EYE_C", "R_EYE_C", "HAIR", "FACIAL_HAIR", "SHOESIZE").contains(this)

  suspend fun profileDetailsChanged(event: ProfileDetailsChangedEvent) {
    val profileType = event.profileType
    when {
      profileType.isPhysicalAttributesProfileType() -> profileDetailsPhysicalAttributesChanged(event)
      else -> {
        telemetryClient.trackEvent(
          "profile-details-synchronisation-ignored",
          mapOf(
            "offenderNo" to event.offenderIdDisplay,
            "bookingId" to event.bookingId.toString(),
            "profileType" to profileType,
            "reason" to "Profile type not supported",
          ),
        )
      }
    }
  }

  suspend fun profileDetailsPhysicalAttributesChanged(event: ProfileDetailsChangedEvent) {
    val profileType = event.profileType
    val offenderNo = event.offenderIdDisplay
    val bookingId = event.bookingId
    val telemetry = mutableMapOf(
      "offenderNo" to offenderNo,
      "bookingId" to bookingId.toString(),
      "profileType" to profileType,
    )

    val dpsResponse = try {
      val nomisResponse = nomisApiService.getProfileDetails(offenderNo)

      val booking = nomisResponse.bookings.find { it.bookingId == bookingId }
        ?: throw ProfileDetailsChangedException("Booking with requested bookingId not found")
      val profileDetails = booking.profileDetails.firstOrNull { it.type == profileType }
        ?: throw ProfileDetailsChangedException("Profile details for requested profileType not found")

      getIgnoreReason(nomisResponse.bookings.size, profileDetails)
        ?.let { ignoreReason ->
          telemetry["reason"] = ignoreReason
          telemetryClient.trackEvent("profile-details-physical-attributes-synchronisation-ignored", telemetry)
          return
        }

      // TODO SDIT-2019 change to align with the DPS API when it is ready
      val (createdAt, createdBy) = getCreated(profileDetails)
      dpsApiService.syncProfileDetailsPhysicalAttributes(
        SyncProfileDetailsPhysicalAttributesRequest(
          prisonerNumber = offenderNo,
          profileType = profileType,
          profileCode = profileDetails.code,
          appliesFrom = booking.startDateTime.toLocalDateTime(),
          appliesTo = booking.endDateTime?.toLocalDateTime(),
          latestBooking = booking.latestBooking,
          createdAt = createdAt.toLocalDateTime(),
          createdBy = createdBy,
        ),
      )
    } catch (e: Exception) {
      telemetry["error"] = e.message.toString()
      telemetryClient.trackEvent("profile-details-physical-attributes-synchronisation-error", telemetry)
      throw e
    }

    telemetryClient.trackEvent("profile-details-physical-attributes-synchronisation-updated", telemetry)
  }

  private fun getIgnoreReason(
    bookingCount: Int,
    profileDetails: ProfileDetailsResponse,
  ): String? =
    if (bookingCount == 1 && profileDetails.code == null && profileDetails.modifiedDateTime == null) {
      "New profile details are empty"
    } else if (profileDetails.auditModuleName == synchronisationUser) {
      "Profile details were created by $synchronisationUser"
    } else {
      null
    }

  private fun getCreated(profileDetails: ProfileDetailsResponse) =
    with(profileDetails) {
      (modifiedDateTime ?: createDateTime) to (modifiedBy ?: createdBy)
    }

  private fun String.toLocalDateTime() = LocalDateTime.parse(this)
}

class ProfileDetailsChangedException(message: String) : IllegalArgumentException(message)
