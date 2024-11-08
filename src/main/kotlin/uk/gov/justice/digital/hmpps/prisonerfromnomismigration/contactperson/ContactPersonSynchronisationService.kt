package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.CreateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ContactPerson

@Service
class ContactPersonSynchronisationService(
  private val mappingApiService: ContactPersonMappingApiService,
  private val nomisApiService: ContactPersonNomisApiService,
  private val dpsApiService: ContactPersonDpsApiService,
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
      mutableMapOf("nomisPersonId" to event.personId)
    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-person-synchronisation-created-skipped",
        telemetry,
      )
    } else {
      mappingApiService.getByNomisPersonIdOrNull(nomisPersonId = event.personId)?.also {
        telemetryClient.trackEvent(
          "contactperson-person-synchronisation-created-ignored",
          telemetry + ("dpsContactId" to it.dpsId),
        )
      } ?: run {
        nomisApiService.getPerson(nomisPersonId = event.personId).also { nomisPerson ->
          val dpsContact = dpsApiService.createContact(nomisPerson.toDpsCreateContactRequest()).also {
            telemetry["dpsContactId"] = it.id
          }
          val mapping = PersonMappingDto(
            nomisId = event.personId,
            dpsId = dpsContact.id.toString(),
            mappingType = PersonMappingDto.MappingType.NOMIS_CREATED,
          )

          // TODO handle create failures
          mappingApiService.createPersonMapping(mapping)
        }
        telemetryClient.trackEvent(
          "contactperson-person-synchronisation-created-success",
          telemetry,
        )
      }
    }
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

  suspend fun personPhoneAdded(event: PersonPhoneEvent) {
    val telemetry =
      mapOf("personId" to event.personId, "phoneId" to event.phoneId)

    if (event.isAddress) {
      telemetryClient.trackEvent(
        "contactperson-person-address-phone-synchronisation-created-todo",
        telemetry,
      )
    } else {
      telemetryClient.trackEvent(
        "contactperson-person-phone-synchronisation-created-success",
        telemetry,
      )
    }
  }

  suspend fun personPhoneUpdated(event: PersonPhoneEvent) {
    val telemetry =
      mapOf("personId" to event.personId, "phoneId" to event.phoneId)
    if (event.isAddress) {
      telemetryClient.trackEvent(
        "contactperson-person-address-phone-synchronisation-updated-todo",
        telemetry,
      )
    } else {
      telemetryClient.trackEvent(
        "contactperson-person-phone-synchronisation-updated-success",
        telemetry,
      )
    }
  }

  suspend fun personPhoneDeleted(event: PersonPhoneEvent) {
    val telemetry =
      mapOf("personId" to event.personId, "phoneId" to event.phoneId)
    if (event.isAddress) {
      telemetryClient.trackEvent(
        "contactperson-person-address-phone-synchronisation-deleted-todo",
        telemetry,
      )
    } else {
      telemetryClient.trackEvent(
        "contactperson-person-phone-synchronisation-deleted-success",
        telemetry,
      )
    }
  }

  suspend fun personEmailAdded(event: PersonInternetAddressEvent) {
    val telemetry =
      mapOf("personId" to event.personId, "internetAddressId" to event.internetAddressId)
    telemetryClient.trackEvent(
      "contactperson-person-email-synchronisation-created-success",
      telemetry,
    )
  }

  suspend fun personEmailUpdated(event: PersonInternetAddressEvent) {
    val telemetry =
      mapOf("personId" to event.personId, "internetAddressId" to event.internetAddressId)
    telemetryClient.trackEvent(
      "contactperson-person-email-synchronisation-updated-success",
      telemetry,
    )
  }

  suspend fun personEmailDeleted(event: PersonInternetAddressEvent) {
    val telemetry =
      mapOf("personId" to event.personId, "internetAddressId" to event.internetAddressId)
    telemetryClient.trackEvent(
      "contactperson-person-email-synchronisation-deleted-success",
      telemetry,
    )
  }

  suspend fun personEmploymentAdded(event: PersonEmploymentEvent) {
    val telemetry =
      mapOf("personId" to event.personId, "employmentSequence" to event.employmentSequence)
    telemetryClient.trackEvent(
      "contactperson-person-employment-synchronisation-created-success",
      telemetry,
    )
  }

  suspend fun personEmploymentUpdated(event: PersonEmploymentEvent) {
    val telemetry =
      mapOf("personId" to event.personId, "employmentSequence" to event.employmentSequence)
    telemetryClient.trackEvent(
      "contactperson-person-employment-synchronisation-updated-success",
      telemetry,
    )
  }

  suspend fun personEmploymentDeleted(event: PersonEmploymentEvent) {
    val telemetry =
      mapOf("personId" to event.personId, "employmentSequence" to event.employmentSequence)
    telemetryClient.trackEvent(
      "contactperson-person-employment-synchronisation-deleted-success",
      telemetry,
    )
  }

  suspend fun personIdentifierAdded(event: PersonIdentifierEvent) {
    val telemetry =
      mapOf("personId" to event.personId, "identifierSequence" to event.identifierSequence)
    telemetryClient.trackEvent(
      "contactperson-person-identifier-synchronisation-created-success",
      telemetry,
    )
  }

  suspend fun personIdentifierUpdated(event: PersonIdentifierEvent) {
    val telemetry =
      mapOf("personId" to event.personId, "identifierSequence" to event.identifierSequence)
    telemetryClient.trackEvent(
      "contactperson-person-identifier-synchronisation-updated-success",
      telemetry,
    )
  }

  suspend fun personIdentifierDeleted(event: PersonIdentifierEvent) {
    val telemetry =
      mapOf("personId" to event.personId, "identifierSequence" to event.identifierSequence)
    telemetryClient.trackEvent(
      "contactperson-person-identifier-synchronisation-deleted-success",
      telemetry,
    )
  }
}

fun ContactPerson.toDpsCreateContactRequest(): CreateContactRequest = CreateContactRequest(
  lastName = this.lastName,
  firstName = this.firstName,
  middleName = this.middleName,
  dateOfBirth = this.dateOfBirth,
  isStaff = this.isStaff == true,
  staff = this.isStaff == true,
  remitter = this.isRemitter == true,
  title = this.title?.code,
  deceasedFlag = this.deceasedDate != null,
  deceasedDate = this.deceasedDate,
  gender = this.gender?.code,
  domesticStatus = this.domesticStatus?.code,
  languageCode = this.language?.code,
  interpreterRequired = this.interpreterRequired,
  createdBy = this.audit.createUsername,
  createdTime = this.audit.createDatetime.toDateTime(),
)

private fun String.toDateTime() = this.let { java.time.LocalDateTime.parse(it) }
private fun PersonEvent.doesOriginateInDps() = this.auditModuleName == "DPS_SYNCHRONISATION"
