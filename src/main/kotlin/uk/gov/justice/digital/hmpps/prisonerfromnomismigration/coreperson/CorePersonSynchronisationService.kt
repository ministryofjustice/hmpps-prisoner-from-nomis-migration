package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonSexualOrientation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails.DomesticStatusChangedException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails.synchronisationUser
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import java.time.LocalDateTime

@Service
class CorePersonSynchronisationService(
  private val telemetryClient: TelemetryClient,
  private val corePersonNomisApiService: CorePersonNomisApiService,
  private val nomisApiService: NomisApiService,
  private val mappingApiService: CorePersonMappingApiService,
  private val corePersonCprApiService: CorePersonCprApiService,
) {
  val eventProfileTypes = listOf("NAT", "NATIO", "SEXO", "DISABILITY", "IMM")

  suspend fun offenderAdded(event: OffenderEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisOffenderId" to event.offenderId,
    )
    telemetryClient.trackEvent("coreperson-offender-synchronisation-added-notimplemented", telemetry)
  }

  suspend fun offenderUpdated(event: OffenderEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisOffenderId" to event.offenderId,
    )
    telemetryClient.trackEvent("coreperson-offender-synchronisation-updated-notimplemented", telemetry)
  }

  suspend fun offenderDeleted(event: OffenderEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisOffenderId" to event.offenderId,
    )
    telemetryClient.trackEvent("coreperson-offender-synchronisation-deleted-notimplemented", telemetry)
  }

  suspend fun offenderBookingAdded(event: OffenderBookingEvent) {
    val telemetry = telemetryOf(
      "nomisOffenderId" to event.offenderId,
      "nomisBookingId" to event.bookingId,
    )
    telemetryClient.trackEvent("coreperson-booking-synchronisation-added-notimplemented", telemetry)
  }

  suspend fun offenderBookingUpdated(event: OffenderBookingEvent) {
    val telemetry = telemetryOf(
      "nomisOffenderId" to event.offenderId,
      "nomisBookingId" to event.bookingId,
    )
    telemetryClient.trackEvent("coreperson-booking-synchronisation-updated-notimplemented", telemetry)
  }

  suspend fun offenderBookingReassigned(event: OffenderBookingReassignedEvent) {
    val telemetry = telemetryOf(
      "nomisOffenderId" to event.offenderId,
      "nomisPreviousOffenderId" to event.previousOffenderId,
      "nomisBookingId" to event.bookingId,
    )
    // Might need to send out event for old offender id as well
    telemetryClient.trackEvent("coreperson-booking-synchronisation-reassigned-notimplemented", telemetry)
  }

  suspend fun offenderSentenceAdded(event: OffenderSentenceEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisBookingId" to event.bookingId,
    )
    telemetryClient.trackEvent("coreperson-sentence-synchronisation-added-notimplemented", telemetry)
  }

  suspend fun offenderSentenceUpdated(event: OffenderSentenceEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisBookingId" to event.bookingId,
    )
    telemetryClient.trackEvent("coreperson-sentence-synchronisation-updated-notimplemented", telemetry)
  }

  suspend fun offenderSentenceDeleted(event: OffenderSentenceEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisBookingId" to event.bookingId,
    )
    telemetryClient.trackEvent("coreperson-sentence-synchronisation-deleted-notimplemented", telemetry)
  }

  suspend fun offenderAddressAdded(event: OffenderAddressEvent) {
    val telemetry = telemetryOf(
      "nomisOffenderId" to event.ownerId,
      "nomisAddressId" to event.addressId,
    )
    telemetryClient.trackEvent("coreperson-address-synchronisation-added-notimplemented", telemetry)
  }

  suspend fun offenderAddressUpdated(event: OffenderAddressEvent) {
    val telemetry = telemetryOf(
      "nomisOffenderId" to event.ownerId,
      "nomisAddressId" to event.addressId,
    )
    telemetryClient.trackEvent("coreperson-address-synchronisation-updated-notimplemented", telemetry)
  }

  suspend fun offenderAddressDeleted(event: OffenderAddressEvent) {
    val telemetry = telemetryOf(
      "nomisOffenderId" to event.ownerId,
      "nomisAddressId" to event.addressId,
    )
    telemetryClient.trackEvent("coreperson-address-synchronisation-deleted-notimplemented", telemetry)
  }

  suspend fun offenderEmailAdded(event: OffenderEmailEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisOffenderId" to event.offenderId,
      "nomisInternetAddressId" to event.internetAddressId,
    )
    telemetryClient.trackEvent("coreperson-email-synchronisation-added-notimplemented", telemetry)
  }

  suspend fun offenderEmailUpdated(event: OffenderEmailEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisOffenderId" to event.offenderId,
      "nomisInternetAddressId" to event.internetAddressId,
    )
    telemetryClient.trackEvent("coreperson-email-synchronisation-updated-notimplemented", telemetry)
  }

  suspend fun offenderEmailDeleted(event: OffenderEmailEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisOffenderId" to event.offenderId,
      "nomisInternetAddressId" to event.internetAddressId,
    )
    telemetryClient.trackEvent("coreperson-email-synchronisation-deleted-notimplemented", telemetry)
  }

  suspend fun offenderPhoneAdded(event: OffenderPhoneEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisOffenderId" to event.offenderId,
      "nomisPhoneId" to event.phoneId,
    )
    telemetryClient.trackEvent("coreperson-phone-synchronisation-added-notimplemented", telemetry)
  }

  suspend fun offenderPhoneUpdated(event: OffenderPhoneEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisOffenderId" to event.offenderId,
      "nomisPhoneId" to event.phoneId,
    )
    telemetryClient.trackEvent("coreperson-phone-synchronisation-updated-notimplemented", telemetry)
  }

  suspend fun offenderPhoneDeleted(event: OffenderPhoneEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisOffenderId" to event.offenderId,
      "nomisPhoneId" to event.phoneId,
    )
    telemetryClient.trackEvent("coreperson-phone-synchronisation-deleted-notimplemented", telemetry)
  }

  suspend fun offenderAddressPhoneAdded(event: OffenderAddressPhoneEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisAddressId" to event.addressId,
      "nomisPhoneId" to event.phoneId,
    )
    telemetryClient.trackEvent("coreperson-addressphone-synchronisation-added-notimplemented", telemetry)
  }

  suspend fun offenderAddressPhoneUpdated(event: OffenderAddressPhoneEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisAddressId" to event.addressId,
      "nomisPhoneId" to event.phoneId,
    )
    telemetryClient.trackEvent("coreperson-addressphone-synchronisation-updated-notimplemented", telemetry)
  }

  suspend fun offenderAddressPhoneDeleted(event: OffenderAddressPhoneEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "nomisAddressId" to event.addressId,
      "nomisPhoneId" to event.phoneId,
    )
    telemetryClient.trackEvent("coreperson-addressphone-synchronisation-deleted-notimplemented", telemetry)
  }

  suspend fun addressUsageAdded(event: AddressUsageEvent) {
    val telemetry = telemetryOf(
      "nomisAddressId" to event.addressId,
      "nomisAddressUsage" to event.addressUsage,
    )
    telemetryClient.trackEvent("coreperson-addressusage-synchronisation-added-notimplemented", telemetry)
  }

  suspend fun addressUsageUpdated(event: AddressUsageEvent) {
    val telemetry = telemetryOf(
      "nomisAddressId" to event.addressId,
      "nomisAddressUsage" to event.addressUsage,
    )
    telemetryClient.trackEvent("coreperson-addressusage-synchronisation-updated-notimplemented", telemetry)
  }

  suspend fun addressUsageDeleted(event: AddressUsageEvent) {
    val telemetry = telemetryOf(
      "nomisAddressId" to event.addressId,
      "nomisAddressUsage" to event.addressUsage,
    )
    telemetryClient.trackEvent("coreperson-addressusage-synchronisation-deleted-notimplemented", telemetry)
  }

  suspend fun offenderProfileDetailsChanged(event: OffenderProfileDetailsEvent) {
    if (eventProfileTypes.contains(event.profileType)) {
      val (offenderIdDisplay, bookingId, profileType) = event
      val telemetry = telemetryOf(
        "offenderNo" to offenderIdDisplay,
        "bookingId" to bookingId,
        "profileType" to profileType,
      )

      val nomisResponse = nomisApiService.getProfileDetails(event.offenderIdDisplay, eventProfileTypes)
      val latestBooking = findBooking(null, nomisResponse.bookings)
      if (latestBooking.bookingId != bookingId) {
        telemetryClient.trackEvent("coreperson-profiledetails-synchronisation-ignored-booking", telemetry)
        return
      }
      val typeHistory = nomisResponse.bookings.flatMap { booking ->
        booking.profileDetails.filter { profile ->
          profile.type == profileType
        }
      }
        .sortedByDescending { it.modifiedDateTime ?: it.createDateTime }

      if (typeHistory.size >= 2 && typeHistory[0].code == typeHistory[1].code) {
        telemetryClient.trackEvent("coreperson-profiledetails-synchronisation-ignored-duplicate", telemetry)
      }

      with(typeHistory[0]) {
        when (profileType) {
          "SEXO" -> {
            val request = PrisonSexualOrientation(
              prisonNumber = offenderIdDisplay,
              sexualOrientationCode = code ?: "",
              current = true, // TBC - redundant?
              createUserId = createdBy,
              createDateTime = createDateTime,
              // TBC createDisplayName = nomisData.displayName,
              modifyDateTime = modifiedDateTime,
              modifyUserId = modifiedBy,
              // TBC modifyDisplayName = nomisData.displayName,
            )
            corePersonCprApiService.syncCreateSexualOrientation(request)
          }
        }
      }
      telemetryClient.trackEvent("coreperson-profiledetails-synchronisation-changed", telemetry)
    }
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

private fun ProfileDetailsResponse.lastModified(): Pair<LocalDateTime, String> = (modifiedDateTime ?: createDateTime) to (modifiedBy ?: createdBy)
