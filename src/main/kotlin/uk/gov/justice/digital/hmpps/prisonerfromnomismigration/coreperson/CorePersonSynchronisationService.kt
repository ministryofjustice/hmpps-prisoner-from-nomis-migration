package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.model.PrisonSexualOrientation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class CorePersonSynchronisationService(
  override val telemetryClient: TelemetryClient,
  private val nomisApiService: NomisApiService,
  private val corePersonCprApiService: CorePersonCprApiService,
) : TelemetryEnabled {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val eventProfileTypes = listOf("NAT", "NATIO", "SEXO", "DISABILITY", "IMM")
  }

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

  suspend fun offenderBeliefAdded(event: OffenderBeliefEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "rootOffenderId" to event.rootOffenderId,
      "nomisOffenderBeliefId" to event.offenderBeliefId,
    )
    telemetryClient.trackEvent("coreperson-belief-synchronisation-added-notimplemented", telemetry)
  }

  suspend fun offenderBeliefUpdated(event: OffenderBeliefEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "rootOffenderId" to event.rootOffenderId,
      "nomisOffenderBeliefId" to event.offenderBeliefId,
    )
    telemetryClient.trackEvent("coreperson-belief-synchronisation-updated-notimplemented", telemetry)
  }

  suspend fun offenderBeliefDeleted(event: OffenderBeliefEvent) {
    val telemetry = telemetryOf(
      "nomisPrisonNumber" to event.offenderIdDisplay,
      "rootOffenderId" to event.rootOffenderId,
      "nomisOffenderBeliefId" to event.offenderBeliefId,
    )
    telemetryClient.trackEvent("coreperson-belief-synchronisation-deleted-notimplemented", telemetry)
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
    val (offenderIdDisplay, bookingId, profileType) = event
    val telemetry = telemetryOf(
      "offenderNo" to offenderIdDisplay,
      "bookingId" to bookingId.toString(),
      "profileType" to profileType,
    )
    if (eventProfileTypes.contains(profileType)) {
      track("coreperson-profiledetails-synchronisation", telemetry) {
        val nomisResponse = nomisApiService.getProfileDetails(event.offenderIdDisplay, listOf(event.profileType))
        val latestBooking = findBooking(null, nomisResponse.bookings)
        if (latestBooking.bookingId != bookingId) {
          telemetryClient.trackEvent("coreperson-profiledetails-synchronisation-ignored-booking", telemetry)
          return
        }

        val typeHistory = nomisResponse.bookings
          .mapNotNull { booking ->
            booking.profileDetails
              .find { profile -> profile.type == profileType }
              ?.let { booking to it }
          }
          .sortedWith(
            compareByDescending<Pair<BookingProfileDetailsResponse, ProfileDetailsResponse>> {
              it.second.modifiedDateTime ?: it.second.createDateTime
            }
              .thenByDescending { it.first.bookingId },
          )
        // NOTE the create/modify dates are COPIED from one booking to another, so do not refer to the row insert or update time

        // The profile that triggered the event is on the latest booking;
        // Is the first profileType in the list, i.e. the most recently updated, the same one?
        if (!typeHistory[0].first.latestBooking) {
          typeHistory.mapIndexed { index, it ->
            telemetry["typeHistory-$index-bookingId"] = it.first.bookingId
            telemetry["typeHistory-$index-latestBooking"] = it.first.latestBooking
            telemetry["typeHistory-$index-startDateTime"] = it.first.startDateTime
            telemetry["typeHistory-$index-profile"] = it.second
          }
          throw BookingException("Most recent update is not for the current booking")
        }

        if (firstIsADuplicate(typeHistory)) {
          telemetryClient.trackEvent("coreperson-profiledetails-synchronisation-ignored-duplicate", telemetry)
          return
        }

        with(typeHistory[0].second) {
          when (profileType) {
            "SEXO" -> {
              corePersonCprApiService.syncCreateSexualOrientation(
                PrisonSexualOrientation(
                  prisonNumber = offenderIdDisplay,
                  sexualOrientationCode = code ?: ""
                    .also {
                      // Not sure how rare this is
                      log.warn("offenderProfileDetailsChanged(): Null value for offender $offenderIdDisplay, booking $bookingId, profile $profileType")
                    },
                  current = true, // TBC - redundant?
                  createUserId = modifiedBy ?: createdBy,
                  createDateTime = modifiedDateTime ?: createDateTime,
                ),
              )
            }
          }
        }
      }
    } else {
      telemetryClient.trackEvent("coreperson-profiledetails-synchronisation-ignored-type", telemetry)
    }
  }
}

private fun firstIsADuplicate(typeHistory: List<Pair<BookingProfileDetailsResponse, ProfileDetailsResponse>>): Boolean = typeHistory.size >= 2 && typeHistory[0].second.code == typeHistory[1].second.code

private fun findBooking(
  bookingId: Long?,
  bookings: List<BookingProfileDetailsResponse>,
): BookingProfileDetailsResponse {
  val booking = if (bookingId != null) {
    bookings.find { it.bookingId == bookingId }
      ?: throw BookingException("No booking found for bookingId $bookingId")
  } else {
    bookings.find { it.latestBooking }
      ?: throw BookingException("Could not find latest booking")
  }
  return booking
}

class BookingException(message: String) : IllegalArgumentException(message)
