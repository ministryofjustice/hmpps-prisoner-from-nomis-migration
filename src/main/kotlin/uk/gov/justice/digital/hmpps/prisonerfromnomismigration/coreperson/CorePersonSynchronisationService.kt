package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent

@Service
class CorePersonSynchronisationService(
  private val telemetryClient: TelemetryClient,
) {
  val eventProfileTypes = setOf("NAT", "NATIO", "SEXO", "DISABILITY", "IMM")

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

  fun offenderPhysicalDetailsChanged(event: OffenderProfileDetailsEvent) {
    if (eventProfileTypes.contains(event.profileType)) {
      val telemetry = telemetryOf(
        "nomisPrisonNumber" to event.offenderIdDisplay,
        "nomisBookingId" to event.bookingId,
        "nomisProfileType" to event.profileType,
      )
      telemetryClient.trackEvent("coreperson-profiledetails-synchronisation-changed-notimplemented", telemetry)
    }
  }
}
