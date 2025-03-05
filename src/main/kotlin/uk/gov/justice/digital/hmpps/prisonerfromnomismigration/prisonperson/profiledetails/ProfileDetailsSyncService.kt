package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.ProfileDetailsChangedEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.atPrisonPersonZone
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.ProfileDetailsPhysicalAttributesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.model.SyncValueWithMetadataString
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.synchronisationUser
import java.time.LocalDateTime

@Service
class ProfileDetailsSyncService(
  private val nomisApiService: ProfileDetailsNomisApiService,
  private val dpsApiService: ProfileDetailPhysicalAttributesDpsApiService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun profileDetailsChanged(event: ProfileDetailsChangedEvent) {
    val profileType = event.profileType
    when {
      profileType.isPhysicalAttributesProfileType() -> profileDetailsPhysicalAttributesChangedEvent(event)
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

  suspend fun profileDetailsPhysicalAttributesChangedEvent(event: ProfileDetailsChangedEvent) = profileDetailsPhysicalAttributesChanged(
    profileType = event.profileType,
    offenderNo = event.offenderIdDisplay,
    bookingId = event.bookingId,
  )

  suspend fun profileDetailsPhysicalAttributesChanged(
    profileType: String,
    offenderNo: String,
    bookingId: Long,
    nomisProfileDetails: PrisonerProfileDetailsResponse? = null,
    forceSync: Boolean = false,
  ) {
    val telemetry = mutableMapOf(
      "offenderNo" to offenderNo,
      "bookingId" to bookingId.toString(),
      "profileType" to profileType,
    )

    val dpsResponse = try {
      val nomisResponse = nomisProfileDetails ?: nomisApiService.getProfileDetails(offenderNo)

      val booking = nomisResponse.bookings.find { it.bookingId == bookingId }
        ?: throw ProfileDetailsChangedException("Booking with requested bookingId not found")
      val profileDetails = booking.profileDetails.firstOrNull { it.type == profileType }
        ?: throw ProfileDetailsChangedException("Profile details for requested profileType not found")

      if (!forceSync) {
        getIgnoreReason(nomisResponse.bookings.size, profileDetails)
          ?.let { ignoreReason ->
            telemetry["reason"] = ignoreReason
            telemetryClient.trackEvent("profile-details-physical-attributes-synchronisation-ignored", telemetry)
            return
          }
      }

      dpsApiService.syncProfileDetailsPhysicalAttributes(
        prisonerNumber = offenderNo,
        with(profileDetails) {
          ProfileDetailsPhysicalAttributesSyncRequest(
            appliesFrom = booking.startDateTime.atPrisonPersonZone(),
            // We no longer return booking end date from the API call and this service isn't used anyway
            appliesTo = "",
            latestBooking = booking.latestBooking,
            build = toDpsRequestIfType("BUILD"),
            shoeSize = toDpsRequestIfType("SHOESIZE"),
            hair = toDpsRequestIfType("HAIR"),
            facialHair = toDpsRequestIfType("FACIAL_HAIR"),
            face = toDpsRequestIfType("FACE"),
            leftEyeColour = toDpsRequestIfType("L_EYE_C"),
            rightEyeColour = toDpsRequestIfType("R_EYE_C"),
          )
        },
      )
    } catch (e: Exception) {
      telemetry["error"] = e.message.toString()
      telemetryClient.trackEvent("profile-details-physical-attributes-synchronisation-error", telemetry)
      throw e
    }

    telemetry["physicalAttributesHistoryId"] = dpsResponse.fieldHistoryInserted.toString()
    telemetryClient.trackEvent("profile-details-physical-attributes-synchronisation-updated", telemetry)
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

  private fun ProfileDetailsResponse.toDpsRequestIfType(profileType: String) = takeIf { type == profileType }
    ?.let {
      val (lastModifiedAt, lastModifiedBy) = it.lastModified()
      SyncValueWithMetadataString(
        value = it.code,
        lastModifiedAt = lastModifiedAt.atPrisonPersonZone(),
        lastModifiedBy = lastModifiedBy,
      )
    }

  private fun ProfileDetailsResponse.lastModified(): Pair<LocalDateTime, String> = (modifiedDateTime ?: createDateTime) to (modifiedBy ?: createdBy)
}

class ProfileDetailsChangedException(message: String) : IllegalArgumentException(message)

internal fun String.isPhysicalAttributesProfileType() = listOf("BUILD", "FACE", "L_EYE_C", "R_EYE_C", "HAIR", "FACIAL_HAIR", "SHOESIZE").contains(this)

internal fun ProfileDetailsResponse.lastModifiedDateTime() = modifiedDateTime ?: createDateTime
