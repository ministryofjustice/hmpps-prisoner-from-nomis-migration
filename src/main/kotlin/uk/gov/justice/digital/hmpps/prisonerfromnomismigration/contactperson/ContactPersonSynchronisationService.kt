package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_PERSON_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreatePrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ContactPerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PersonContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType

@Service
class ContactPersonSynchronisationService(
  private val mappingApiService: ContactPersonMappingApiService,
  private val nomisApiService: ContactPersonNomisApiService,
  private val dpsApiService: ContactPersonDpsApiService,
  private val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

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
      mutableMapOf("offenderNo" to event.offenderIdDisplay, "bookingId" to event.bookingId, "nomisPersonId" to event.personId, "dpsContactId" to event.personId, "nomisContactId" to event.contactId)

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-contact-synchronisation-created-skipped",
        telemetry,
      )
    } else {
      mappingApiService.getByNomisContactIdOrNull(nomisContactId = event.contactId)?.also {
        telemetryClient.trackEvent(
          "contactperson-contact-synchronisation-created-ignored",
          telemetry + ("dpsPrisonerContactId" to it.dpsId),
        )
      } ?: run {
        nomisApiService.getPerson(nomisPersonId = event.personId).also { nomisPerson ->
          val nomisContact = nomisPerson.contacts.find { it.id == event.contactId } ?: throw IllegalStateException("Contact ${event.contactId} for person ${event.personId} not found in NOMIS")
          val dpsPrisonerContact = dpsApiService.createPrisonerContact(nomisContact.toDpsCreatePrisonerContactRequest(nomisPersonId = event.personId)).also {
            telemetry["dpsPrisonerContactId"] = it.id
          }
          val mapping = PersonContactMappingDto(
            nomisId = event.contactId,
            dpsId = dpsPrisonerContact.id.toString(),
            mappingType = PersonContactMappingDto.MappingType.NOMIS_CREATED,
          )

          tryToCreateMapping(mapping, telemetry)
        }
        telemetryClient.trackEvent(
          "contactperson-contact-synchronisation-created-success",
          telemetry,
        )
      }
    }
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

          tryToCreateMapping(mapping, telemetry)
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
      mutableMapOf("nomisPersonId" to event.personId, "dpsContactId" to event.personId, "nomisAddressId" to event.addressId)

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-address-synchronisation-created-skipped",
        telemetry,
      )
    } else {
      mappingApiService.getByNomisAddressIdOrNull(nomisAddressId = event.addressId)?.also {
        telemetryClient.trackEvent(
          "contactperson-address-synchronisation-created-ignored",
          telemetry + ("dpsContactAddressId" to it.dpsId),
        )
      } ?: run {
        nomisApiService.getPerson(nomisPersonId = event.personId).also { nomisPerson ->
          val nomisAddress = nomisPerson.addresses.find { it.addressId == event.addressId } ?: throw IllegalStateException("Address ${event.addressId} for person ${event.personId} not found in NOMIS")
          val dpsAddress = dpsApiService.createContactAddress(nomisAddress.toDpsCreateContactAddressRequest(nomisPersonId = event.personId)).also {
            telemetry["dpsContactAddressId"] = it.contactAddressId
          }
          val mapping = PersonAddressMappingDto(
            nomisId = event.addressId,
            dpsId = dpsAddress.contactAddressId.toString(),
            mappingType = PersonAddressMappingDto.MappingType.NOMIS_CREATED,
          )

          tryToCreateMapping(mapping, telemetry)
        }
        telemetryClient.trackEvent(
          "contactperson-address-synchronisation-created-success",
          telemetry,
        )
      }
    }
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

  private suspend fun tryToCreateMapping(
    mapping: PersonMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createPersonMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for person id $mapping", e)
      queueService.sendMessage(
        messageType = RETRY_SYNCHRONISATION_PERSON_MAPPING.name,
        synchronisationType = SynchronisationType.CONTACTPERSON,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }
  private suspend fun tryToCreateMapping(
    mapping: PersonContactMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createContactMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for contact id $mapping", e)
      queueService.sendMessage(
        messageType = ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_CONTACT_MAPPING.name,
        synchronisationType = SynchronisationType.CONTACTPERSON,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }
  private suspend fun tryToCreateMapping(
    mapping: PersonAddressMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createAddressMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for address id $mapping", e)
      queueService.sendMessage(
        messageType = ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_ADDRESS_MAPPING.name,
        synchronisationType = SynchronisationType.CONTACTPERSON,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  private suspend fun createPersonMapping(
    mapping: PersonMappingDto,
  ) {
    mappingApiService.createPersonMapping(mapping).takeIf { it.isError }?.also {
      with(it.errorResponse!!.moreInfo) {
        telemetryClient.trackEvent(
          "from-nomis-sync-contactperson-duplicate",
          mapOf(
            "existingNomisPersonId" to existing.nomisId,
            "existingDpsContactId" to existing.dpsId,
            "duplicateNomisPersonId" to duplicate.nomisId,
            "duplicateDpsContactId" to duplicate.dpsId,
            "type" to "PERSON",
          ),
        )
      }
    }
  }
  private suspend fun createContactMapping(
    mapping: PersonContactMappingDto,
  ) {
    mappingApiService.createContactMapping(mapping).takeIf { it.isError }?.also {
      with(it.errorResponse!!.moreInfo) {
        telemetryClient.trackEvent(
          "from-nomis-sync-contactperson-duplicate",
          mapOf(
            "existingNomisContactId" to existing.nomisId,
            "existingDpsPrisonerContactId" to existing.dpsId,
            "duplicateNomisContactId" to duplicate.nomisId,
            "duplicateDpsPrisonerContactId" to duplicate.dpsId,
            "type" to "CONTACT",
          ),
        )
      }
    }
  }
  private suspend fun createAddressMapping(
    mapping: PersonAddressMappingDto,
  ) {
    mappingApiService.createAddressMapping(mapping).takeIf { it.isError }?.also {
      with(it.errorResponse!!.moreInfo) {
        telemetryClient.trackEvent(
          "from-nomis-sync-contactperson-duplicate",
          mapOf(
            "existingNomisAddressId" to existing.nomisId,
            "existingDpsContactAddressId" to existing.dpsId,
            "duplicateNomisAddressId" to duplicate.nomisId,
            "duplicateDpsContactAddressId" to duplicate.dpsId,
            "type" to "ADDRESS",
          ),
        )
      }
    }
  }

  suspend fun retryCreatePersonMapping(retryMessage: InternalMessage<PersonMappingDto>) {
    createPersonMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent(
          "contactperson-person-mapping-synchronisation-created",
          retryMessage.telemetryAttributes,
        )
      }
  }
  suspend fun retryCreateContactMapping(retryMessage: InternalMessage<PersonContactMappingDto>) {
    createContactMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent(
          "contactperson-contact-mapping-synchronisation-created",
          retryMessage.telemetryAttributes,
        )
      }
  }
  suspend fun retryCreateAddressMapping(retryMessage: InternalMessage<PersonAddressMappingDto>) {
    createAddressMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent(
          "contactperson-address-mapping-synchronisation-created",
          retryMessage.telemetryAttributes,
        )
      }
  }
}

fun ContactPerson.toDpsCreateContactRequest() = SyncCreateContactRequest(
  personId = this.personId,
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

fun PersonContact.toDpsCreatePrisonerContactRequest(nomisPersonId: Long) = SyncCreatePrisonerContactRequest(
  contactId = nomisPersonId,
  prisonerNumber = this.prisoner.offenderNo,
  contactType = this.contactType.code,
  relationshipType = this.relationshipType.code,
  active = this.active,
  currentTerm = this.prisoner.bookingSequence == 1L,
  nextOfKin = this.nextOfKin,
  emergencyContact = this.emergencyContact,
  approvedVisitor = this.approvedVisitor,
  comments = this.comment,
  expiryDate = this.expiryDate,
  createdBy = this.audit.createUsername,
  createdTime = this.audit.createDatetime.toDateTime(),
)

fun PersonAddress.toDpsCreateContactAddressRequest(nomisPersonId: Long) = SyncCreateContactAddressRequest(
  contactId = nomisPersonId,
  // for now default to HOME until DPS make this nullable
  addressType = this.type?.code ?: "HOME",
  primaryAddress = this.primaryAddress,
  flat = this.flat,
  property = this.premise,
  street = this.street,
  area = this.locality,
  cityCode = this.city?.code,
  countyCode = this.county?.code,
  countryCode = this.country?.code,
  postcode = this.postcode,
  verified = null,
  mailFlag = this.mailAddress,
  startDate = this.startDate,
  endDate = this.endDate,
  noFixedAddress = this.noFixedAddress,
  comments = this.comment,
  createdBy = this.audit.createUsername,
  createdTime = this.audit.createDatetime.toDateTime(),
)

private fun String.toDateTime() = this.let { java.time.LocalDateTime.parse(it) }
private fun EventAudited.doesOriginateInDps() = this.auditModuleName == "DPS_SYNCHRONISATION"
