package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.ProfileDetailsChangedEvent

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
        ?: throw ProfileDetailsChangedException("Booking with profile details not found for bookingId=$bookingId")
      val physicalAttributes = booking.profileDetails.firstOrNull { it.type == profileType }

      // TODO implement this when the DPS API is ready to call
      dpsApiService.syncProfileDetailsPhysicalAttributes(offenderNo)
    } catch (e: Exception) {
      telemetry["error"] = e.message.toString()
      telemetryClient.trackEvent("profile-details-physical-attributes-synchronisation-error", telemetry)
      throw e
    }

    telemetryClient.trackEvent("profile-details-physical-attributes-synchronisation-updated", telemetry)
  }
}

class ProfileDetailsChangedException(message: String) : IllegalArgumentException(message)
