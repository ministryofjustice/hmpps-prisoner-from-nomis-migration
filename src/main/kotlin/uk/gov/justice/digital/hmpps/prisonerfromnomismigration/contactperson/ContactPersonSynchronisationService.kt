package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent

@Service
class ContactPersonSynchronisationService(
  private val telemetryClient: TelemetryClient,
) {
  suspend fun personRestrictionUpserted(event: PersonRestrictionEvent) {
    val telemetry =
      mapOf("personRestrictionId" to event.visitorRestrictionId, "personId" to event.personId)
    telemetryClient.trackEvent(
      // TODO - created or updated
      "contactperson-person-restriction-synchronisation-created-success",
      telemetry,
    )
  }
  suspend fun personRestrictionDeleted(event: PersonRestrictionEvent) {
    val telemetry =
      mapOf("personRestrictionId" to event.visitorRestrictionId, "personId" to event.personId)
    telemetryClient.trackEvent(
      "contactperson-person-restriction-synchronisation-deleted-success",
      telemetry,
    )
  }
  suspend fun contactAdded(event: ContactEvent) {
    val telemetry =
      mapOf("offenderNo" to event.offenderIdDisplay, "bookingId" to event.bookingId, "personId" to event.personId, "contactId" to event.contactId)
    telemetryClient.trackEvent(
      "contactperson-contact-synchronisation-created-success",
      telemetry,
    )
  }
  suspend fun contactUpdated(event: ContactEvent) {
    val telemetry =
      mapOf("offenderNo" to event.offenderIdDisplay, "bookingId" to event.bookingId, "personId" to event.personId, "contactId" to event.contactId)
    telemetryClient.trackEvent(
      "contactperson-contact-synchronisation-updated-success",
      telemetry,
    )
  }
  suspend fun contactDeleted(event: ContactEvent) {
    val telemetry =
      mapOf("offenderNo" to event.offenderIdDisplay, "bookingId" to event.bookingId, "personId" to event.personId, "contactId" to event.contactId)
    telemetryClient.trackEvent(
      "contactperson-contact-synchronisation-deleted-success",
      telemetry,
    )
  }
  suspend fun contactRestrictionUpserted(event: ContactRestrictionEvent) {
    val telemetry =
      mapOf("offenderNo" to event.offenderIdDisplay, "contactRestrictionId" to event.offenderPersonRestrictionId, "personId" to event.personId, "contactId" to event.contactPersonId)
    telemetryClient.trackEvent(
      // TODO - created or updated
      "contactperson-contact-restriction-synchronisation-created-success",
      telemetry,
    )
  }
  suspend fun contactRestrictionDeleted(event: ContactRestrictionEvent) {
    val telemetry =
      mapOf("offenderNo" to event.offenderIdDisplay, "contactRestrictionId" to event.offenderPersonRestrictionId, "personId" to event.personId, "contactId" to event.contactPersonId)
    telemetryClient.trackEvent(
      // TODO - created or updated
      "contactperson-contact-restriction-synchronisation-deleted-success",
      telemetry,
    )
  }
  suspend fun personAdded(event: PersonEvent) {
    val telemetry =
      mapOf("personId" to event.personId)
    telemetryClient.trackEvent(
      "contactperson-person-synchronisation-created-success",
      telemetry,
    )
  }

  suspend fun personUpdated(event: PersonEvent) {
    val telemetry =
      mapOf("personId" to event.personId)
    telemetryClient.trackEvent(
      "contactperson-person-synchronisation-updated-success",
      telemetry,
    )
  }

  suspend fun personDeleted(event: PersonEvent) {
    val telemetry =
      mapOf("personId" to event.personId)
    telemetryClient.trackEvent(
      "contactperson-person-synchronisation-deleted-success",
      telemetry,
    )
  }
  suspend fun personAddressAdded(event: PersonAddressEvent) {
    val telemetry =
      mapOf("personId" to event.personId, "addressId" to event.addressId)
    telemetryClient.trackEvent(
      "contactperson-person-address-synchronisation-created-success",
      telemetry,
    )
  }

  suspend fun personAddressUpdated(event: PersonAddressEvent) {
    val telemetry =
      mapOf("personId" to event.personId, "addressId" to event.addressId)
    telemetryClient.trackEvent(
      "contactperson-person-address-synchronisation-updated-success",
      telemetry,
    )
  }

  suspend fun personAddressDeleted(event: PersonAddressEvent) {
    val telemetry =
      mapOf("personId" to event.personId, "addressId" to event.addressId)
    telemetryClient.trackEvent(
      "contactperson-person-address-synchronisation-deleted-success",
      telemetry,
    )
  }
}
