package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerBookingMovedDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerMergeDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.PrisonerReceiveDomainEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.doesOriginateInDps
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.valuesAsStrings
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonPrisonerMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonSimpleMappingIdDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonContactRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonEmailMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonEmploymentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonPhoneMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PersonRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactPerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonEmailAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonEmployment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonIdentifier
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonPhoneNumber
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RETRY_REPLACE_PRISONER_PERSON_BOOKING_CHANGED_MAPPINGS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RETRY_REPLACE_PRISONER_PERSON_BOOKING_MOVED_MAPPINGS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RETRY_REPLACE_PRISONER_PERSON_PRISONER_MERGED_MAPPINGS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_PERSON_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.CodedValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MergePrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.ResetPrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactIdentityRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreateEmploymentRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreatePrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncCreatePrisonerContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncPrisonerRelationship
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncRelationshipRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactIdentityRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdateEmploymentRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdatePrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncUpdatePrisonerContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.InternalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationQueueService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import java.time.LocalDateTime

@Service
class ContactPersonSynchronisationService(
  private val mappingApiService: ContactPersonMappingApiService,
  private val nomisApiService: ContactPersonNomisApiService,
  private val dpsApiService: ContactPersonDpsApiService,
  override val telemetryClient: TelemetryClient,
  private val queueService: SynchronisationQueueService,
) : TelemetryEnabled {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun personRestrictionUpserted(event: PersonRestrictionEvent) {
    val telemetry = telemetryOf(
      "nomisPersonId" to event.personId,
      "dpsContactId" to event.personId,
      "nomisPersonRestrictionId" to event.visitorRestrictionId,
    )

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-person-restriction-synchronisation-upserted-skipped",
        telemetry,
      )
    } else {
      val existingMapping = mappingApiService.getByNomisPersonRestrictionIdOrNull(nomisPersonRestrictionId = event.visitorRestrictionId)
      val telemetryName = if (existingMapping != null) "contactperson-person-restriction-synchronisation-updated" else "contactperson-person-restriction-synchronisation-created"

      track(telemetryName, telemetry) {
        val nomisPerson = nomisApiService.getPerson(nomisPersonId = event.personId)
        val nomisRestriction = nomisPerson.restrictions.find { it.id == event.visitorRestrictionId }
          ?: throw IllegalStateException("Restriction ${event.visitorRestrictionId} for person ${event.personId} not found in NOMIS")

        existingMapping?.also {
          telemetry["dpsContactRestrictionId"] = it.dpsId
          dpsApiService.updateContactRestriction(
            contactRestrictionId = it.dpsId.toLong(),
            nomisRestriction.toDpsUpdateContactRestrictionRequest(dpsContactId = event.personId),
          )
        } ?: run {
          val dpsContactRestriction =
            dpsApiService.createContactRestriction(nomisRestriction.toDpsCreateContactRestrictionRequest(dpsContactId = event.personId))
              .also {
                telemetry["dpsContactRestrictionId"] = it.contactRestrictionId
              }
          val mapping = PersonRestrictionMappingDto(
            nomisId = event.visitorRestrictionId,
            dpsId = dpsContactRestriction.contactRestrictionId.toString(),
            mappingType = PersonRestrictionMappingDto.MappingType.NOMIS_CREATED,
          )

          tryToCreateMapping(mapping, telemetry)
        }
      }
    }
  }
  suspend fun personRestrictionDeleted(event: PersonRestrictionEvent) {
    val telemetry = telemetryOf(
      "nomisPersonId" to event.personId,
      "dpsContactId" to event.personId,
      "nomisPersonRestrictionId" to event.visitorRestrictionId,
    )
    mappingApiService.getByNomisPersonRestrictionIdOrNull(nomisPersonRestrictionId = event.visitorRestrictionId)?.also {
      track("contactperson-person-restriction-synchronisation-deleted", telemetry) {
        telemetry["dpsContactRestrictionId"] = it.dpsId
        dpsApiService.deleteContactRestriction(it.dpsId.toLong())
        mappingApiService.deleteByNomisPersonRestrictionId(event.visitorRestrictionId)
      }
    } ?: run {
      telemetryClient.trackEvent(
        "contactperson-person-restriction-synchronisation-deleted-ignored",
        telemetry,
      )
    }
  }
  suspend fun contactAdded(event: ContactEvent) {
    val telemetry =
      telemetryOf("offenderNo" to event.offenderIdDisplay, "bookingId" to event.bookingId, "nomisPersonId" to event.personId, "dpsContactId" to event.personId, "nomisContactId" to event.contactId)

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
        track("contactperson-contact-synchronisation-created", telemetry) {
          nomisApiService.getPerson(nomisPersonId = event.personId).also { nomisPerson ->
            val nomisContact = nomisPerson.contacts.find { it.id == event.contactId }
              ?: throw IllegalStateException("Contact ${event.contactId} for person ${event.personId} not found in NOMIS")
            val dpsPrisonerContact =
              dpsApiService.createPrisonerContact(nomisContact.toDpsCreatePrisonerContactRequest(nomisPersonId = event.personId))
                .also {
                  telemetry["dpsPrisonerContactId"] = it.id
                }
            val mapping = PersonContactMappingDto(
              nomisId = event.contactId,
              dpsId = dpsPrisonerContact.id.toString(),
              mappingType = PersonContactMappingDto.MappingType.NOMIS_CREATED,
            )

            tryToCreateMapping(mapping, telemetry)
          }
        }
      }
    }
  }
  suspend fun contactUpdated(event: ContactEvent) {
    val telemetry =
      telemetryOf(
        "offenderNo" to event.offenderIdDisplay,
        "bookingId" to event.bookingId,
        "nomisPersonId" to event.personId,
        "dpsContactId" to event.personId,
        "nomisContactId" to event.contactId,
      )

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-contact-synchronisation-updated-skipped",
        telemetry,
      )
    } else {
      track("contactperson-contact-synchronisation-updated", telemetry) {
        val dpsPrisonerContactId =
          mappingApiService.getByNomisContactId(nomisContactId = event.contactId).dpsId.toLong().also {
            telemetry["dpsPrisonerContactId"] = it
          }
        val nomisPerson = nomisApiService.getPerson(nomisPersonId = event.personId)
        val nomisContact = nomisPerson.contacts.find { it.id == event.contactId }
          ?: throw IllegalStateException("Contact ${event.contactId} for person ${event.personId} not found in NOMIS")

        dpsApiService.updatePrisonerContact(
          dpsPrisonerContactId,
          nomisContact.toDpsUpdatePrisonerContactRequest(nomisPersonId = event.personId),
        )
      }
    }
  }
  suspend fun contactDeleted(event: ContactEvent) {
    val telemetry =
      telemetryOf(
        "offenderNo" to event.offenderIdDisplay,
        "bookingId" to event.bookingId,
        "nomisPersonId" to event.personId,
        "dpsContactId" to event.personId,
        "nomisContactId" to event.contactId,
      )
    mappingApiService.getByNomisContactIdOrNull(nomisContactId = event.contactId)?.also {
      track("contactperson-contact-synchronisation-deleted", telemetry) {
        telemetry["dpsPrisonerContactId"] = it.dpsId
        dpsApiService.deletePrisonerContact(it.dpsId.toLong())
        mappingApiService.deleteByNomisContactId(event.contactId)
      }
    } ?: run {
      telemetryClient.trackEvent(
        "contactperson-contact-synchronisation-deleted-ignored",
        telemetry,
      )
    }
  }

  suspend fun contactRestrictionUpserted(event: ContactRestrictionEvent) {
    val telemetry =
      telemetryOf("offenderNo" to (event.offenderIdDisplay ?: "unknown"), "nomisPersonId" to event.personId, "dpsContactId" to event.personId, "nomisContactId" to event.contactPersonId, "nomisContactRestrictionId" to event.offenderPersonRestrictionId)

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-contact-restriction-synchronisation-upserted-skipped",
        telemetry,
      )
    } else {
      val existingMapping = mappingApiService.getByNomisContactRestrictionIdOrNull(nomisContactRestrictionId = event.offenderPersonRestrictionId)
      val telemetryName = if (existingMapping != null) "contactperson-contact-restriction-synchronisation-updated" else "contactperson-contact-restriction-synchronisation-created"
      track(telemetryName, telemetry) {
        val nomisPerson = nomisApiService.getPerson(nomisPersonId = event.personId)
        val nomisContact = nomisPerson.contacts.find { it.id == event.contactPersonId } ?: throw IllegalStateException("Contact ${event.contactPersonId} for person ${event.personId} not found in NOMIS")
        val nomisRestriction = nomisContact.restrictions.find { it.id == event.offenderPersonRestrictionId } ?: throw IllegalStateException("Contact Restriction ${event.offenderPersonRestrictionId} for person ${event.personId} for contact ${event.contactPersonId} not found in NOMIS")
        val dpsPrisonerContactId = mappingApiService.getByNomisContactId(nomisContact.id).dpsId.toLong()
        existingMapping?.also {
          telemetry["dpsPrisonerContactRestrictionId"] = it.dpsId
          telemetry["dpsPrisonerContactId"] = dpsPrisonerContactId
          dpsApiService.updatePrisonerContactRestriction(prisonerContactRestrictionId = it.dpsId.toLong(), nomisRestriction.toDpsUpdatePrisonerContactRestrictionRequest())
        } ?: run {
          val dpsPrisonerContactRestriction = dpsApiService.createPrisonerContactRestriction(nomisRestriction.toDpsCreatePrisonerContactRestrictionRequest(dpsPrisonerContactId = dpsPrisonerContactId)).also {
            telemetry["dpsPrisonerContactRestrictionId"] = it.prisonerContactRestrictionId
            telemetry["dpsPrisonerContactId"] = dpsPrisonerContactId
          }
          val mapping = PersonContactRestrictionMappingDto(
            nomisId = event.offenderPersonRestrictionId,
            dpsId = dpsPrisonerContactRestriction.prisonerContactRestrictionId.toString(),
            mappingType = PersonContactRestrictionMappingDto.MappingType.NOMIS_CREATED,
          )

          tryToCreateMapping(mapping, telemetry)
        }
      }
    }
  }

  suspend fun contactRestrictionDeleted(event: ContactRestrictionEvent) {
    val telemetry =
      telemetryOf("offenderNo" to (event.offenderIdDisplay ?: "unknown"), "nomisPersonId" to event.personId, "dpsContactId" to event.personId, "nomisContactId" to event.contactPersonId, "nomisContactRestrictionId" to event.offenderPersonRestrictionId)

    mappingApiService.getByNomisContactRestrictionIdOrNull(nomisContactRestrictionId = event.offenderPersonRestrictionId)?.also {
      track("contactperson-contact-restriction-synchronisation-deleted", telemetry) {
        telemetry["dpsPrisonerContactRestrictionId"] = it.dpsId
        dpsApiService.deletePrisonerContactRestriction(it.dpsId.toLong())
        mappingApiService.deleteByNomisContactRestrictionId(event.offenderPersonRestrictionId)
      }
    } ?: run {
      telemetryClient.trackEvent(
        "contactperson-contact-restriction-synchronisation-deleted-ignored",
        telemetry,
      )
    }
  }
  suspend fun personAdded(event: PersonEvent) {
    val telemetry = telemetryOf("nomisPersonId" to event.personId)
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
        track("contactperson-person-synchronisation-created", telemetry) {
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
        }
      }
    }
  }

  suspend fun personUpdated(event: PersonEvent) {
    val telemetry =
      telemetryOf("nomisPersonId" to event.personId)
    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-person-synchronisation-updated-skipped",
        telemetry,
      )
    } else {
      track("contactperson-person-synchronisation-updated", telemetry) {
        val contactId = mappingApiService.getByNomisPersonId(nomisPersonId = event.personId).dpsId.toLong().also {
          telemetry["dpsContactId"] = it
        }
        val nomisPerson = nomisApiService.getPerson(nomisPersonId = event.personId)
        dpsApiService.updateContact(contactId, nomisPerson.toDpsUpdateContactRequest())
      }
    }
  }

  suspend fun personDeleted(event: PersonEvent) {
    val telemetry =
      telemetryOf("nomisPersonId" to event.personId)
    mappingApiService.getByNomisPersonIdOrNull(nomisPersonId = event.personId)?.also {
      track("contactperson-person-synchronisation-deleted", telemetry) {
        telemetry["dpsContactId"] = it.dpsId
        dpsApiService.deleteContact(it.dpsId.toLong())
        mappingApiService.deleteByNomisPersonId(event.personId)
      }
    } ?: run {
      telemetryClient.trackEvent(
        "contactperson-person-synchronisation-deleted-ignored",
        telemetry,
      )
    }
  }

  suspend fun personAddressAdded(event: PersonAddressEvent) {
    val telemetry =
      telemetryOf("nomisPersonId" to event.personId, "dpsContactId" to event.personId, "nomisAddressId" to event.addressId)

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
        track("contactperson-address-synchronisation-created", telemetry) {
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
        }
      }
    }
  }

  suspend fun personAddressUpdated(event: PersonAddressEvent) {
    val telemetry =
      telemetryOf("nomisPersonId" to event.personId, "nomisAddressId" to event.addressId, "dpsContactId" to event.personId)
    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-address-synchronisation-updated-skipped",
        telemetry,
      )
    } else {
      track("contactperson-address-synchronisation-updated", telemetry) {
        val dpsAddressId = mappingApiService.getByNomisAddressId(nomisAddressId = event.addressId).dpsId.toLong().also {
          telemetry["dpsContactAddressId"] = it
        }
        val nomisPerson = nomisApiService.getPerson(nomisPersonId = event.personId)
        val nomisAddress = nomisPerson.addresses.find { it.addressId == event.addressId } ?: throw IllegalStateException("Address ${event.addressId} for person ${event.personId} not found in NOMIS")
        dpsApiService.updateContactAddress(dpsAddressId, nomisAddress.toDpsUpdateContactAddressRequest(nomisPersonId = event.personId))
      }
    }
  }

  suspend fun personAddressDeleted(event: PersonAddressEvent) {
    val telemetry =
      telemetryOf("nomisPersonId" to event.personId, "nomisAddressId" to event.addressId, "dpsContactId" to event.personId)

    mappingApiService.getByNomisAddressIdOrNull(nomisAddressId = event.addressId)?.also {
      track("contactperson-address-synchronisation-deleted", telemetry) {
        telemetry["dpsContactAddressId"] = it.dpsId
        dpsApiService.deleteContactAddress(it.dpsId.toLong())
        mappingApiService.deleteByNomisAddressId(event.addressId)
      }
    } ?: run {
      telemetryClient.trackEvent(
        "contactperson-address-synchronisation-deleted-ignored",
        telemetry,
      )
    }
  }

  suspend fun personPhoneAdded(event: PersonPhoneEvent) {
    val telemetry =
      telemetryOf("nomisPersonId" to event.personId, "dpsContactId" to event.personId, "nomisPhoneId" to event.phoneId)

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-phone-synchronisation-created-skipped",
        telemetry,
      )
    } else {
      mappingApiService.getByNomisPhoneIdOrNull(nomisPhoneId = event.phoneId)?.also {
        telemetryClient.trackEvent(
          "contactperson-phone-synchronisation-created-ignored",
          telemetry + ("dpsContactPhoneId" to it.dpsId),
        )
      } ?: run {
        track("contactperson-phone-synchronisation-created", telemetry) {
          nomisApiService.getPerson(nomisPersonId = event.personId).also { nomisPerson ->
            val mapping = if (event.isAddress && event.addressId != null) {
              createContactAddressPhone(
                nomisPerson = nomisPerson,
                personId = event.personId,
                addressId = event.addressId,
                phoneId = event.phoneId,
                telemetry = telemetry,
              )
            } else {
              createContactPhone(
                nomisPerson = nomisPerson,
                personId = event.personId,
                phoneId = event.phoneId,
                telemetry = telemetry,
              )
            }
            tryToCreateMapping(mapping, telemetry)
          }
        }
      }
    }
  }

  suspend fun createContactPhone(nomisPerson: ContactPerson, personId: Long, phoneId: Long, telemetry: MutableMap<String, Any>): PersonPhoneMappingDto {
    val nomisPhone = nomisPerson.phoneNumbers.find { it.phoneId == phoneId }
      ?: throw IllegalStateException("Phone $phoneId for person $personId not found in NOMIS")
    val dpsPhone =
      dpsApiService.createContactPhone(nomisPhone.toDpsCreateContactPhoneRequest(nomisPersonId = personId))
        .also {
          telemetry["dpsContactPhoneId"] = it.contactPhoneId
        }
    return PersonPhoneMappingDto(
      nomisId = phoneId,
      dpsId = dpsPhone.contactPhoneId.toString(),
      dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
      mappingType = PersonPhoneMappingDto.MappingType.NOMIS_CREATED,
    )
  }

  suspend fun createContactAddressPhone(nomisPerson: ContactPerson, personId: Long, addressId: Long, phoneId: Long, telemetry: MutableMap<String, Any>): PersonPhoneMappingDto {
    val nomisAddress = nomisPerson.addresses.find { it.addressId == addressId }
      ?: throw IllegalStateException("Address $addressId for person $personId not found in NOMIS")

    val dpsAddressId = mappingApiService.getByNomisAddressId(addressId).dpsId.toLong().also {
      telemetry["dpsContactAddressId"] = it
      telemetry["nomisAddressId"] = addressId
    }

    val nomisPhone = nomisAddress.phoneNumbers.find { it.phoneId == phoneId }
      ?: throw IllegalStateException("Phone $phoneId for person $personId on address $addressId not found in NOMIS")

    val dpsPhone =
      dpsApiService.createContactAddressPhone(nomisPhone.toDpsCreateContactAddressPhoneRequest(dpsAddressId = dpsAddressId))
        .also {
          telemetry["dpsContactAddressPhoneId"] = it.contactAddressPhoneId
        }
    return PersonPhoneMappingDto(
      nomisId = phoneId,
      dpsId = dpsPhone.contactAddressPhoneId.toString(),
      dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.ADDRESS,
      mappingType = PersonPhoneMappingDto.MappingType.NOMIS_CREATED,
    )
  }

  suspend fun personPhoneUpdated(event: PersonPhoneEvent) {
    val telemetry =
      telemetryOf("nomisPersonId" to event.personId, "dpsContactId" to event.personId, "nomisPhoneId" to event.phoneId).also {
        if (event.isAddress && event.addressId != null) {
          it["nomisAddressId"] = event.addressId
        }
      }

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-phone-synchronisation-updated-skipped",
        telemetry,
      )
    } else {
      track("contactperson-phone-synchronisation-updated", telemetry) {
        val mapping = mappingApiService.getByNomisPhoneId(nomisPhoneId = event.phoneId).also {
          if (it.dpsPhoneType == PersonPhoneMappingDto.DpsPhoneType.PERSON) {
            telemetry["dpsContactPhoneId"] = it.dpsId
          } else {
            telemetry["dpsContactAddressPhoneId"] = it.dpsId
          }
        }
        val nomisPerson = nomisApiService.getPerson(nomisPersonId = event.personId)

        if (event.isAddress && event.addressId != null) {
          val nomisAddress = nomisPerson.addresses.find { it.addressId == event.addressId }
            ?: throw IllegalStateException("Address ${event.addressId} for person ${event.personId} not found in NOMIS")
          val nomisPhone = nomisAddress.phoneNumbers.find { it.phoneId == event.phoneId }
            ?: throw IllegalStateException("Phone ${event.phoneId} for person ${event.personId} on address $${event.addressId} not found in NOMIS")

          dpsApiService.updateContactAddressPhone(mapping.dpsId.toLong(), nomisPhone.toDpsUpdateContactAddressPhoneRequest())
        } else {
          val nomisPhone = nomisPerson.phoneNumbers.find { it.phoneId == event.phoneId }
            ?: throw IllegalStateException("Phone ${event.phoneId} for person  ${event.personId}not found in NOMIS")

          dpsApiService.updateContactPhone(mapping.dpsId.toLong(), nomisPhone.toDpsUpdateContactPhoneRequest(event.personId))
        }
      }
    }
  }

  suspend fun personPhoneDeleted(event: PersonPhoneEvent) {
    val telemetry =
      telemetryOf("nomisPersonId" to event.personId, "dpsContactId" to event.personId, "nomisPhoneId" to event.phoneId).also {
        if (event.isAddress && event.addressId != null) {
          it["nomisAddressId"] = event.addressId
        }
      }

    mappingApiService.getByNomisPhoneIdOrNull(nomisPhoneId = event.phoneId)?.also {
      if (it.dpsPhoneType == PersonPhoneMappingDto.DpsPhoneType.PERSON) {
        telemetry["dpsContactPhoneId"] = it.dpsId
      } else {
        telemetry["dpsContactAddressPhoneId"] = it.dpsId
      }
      track("contactperson-phone-synchronisation-deleted", telemetry) {
        if (event.isAddress) {
          dpsApiService.deleteContactAddressPhone(it.dpsId.toLong())
        } else {
          dpsApiService.deleteContactPhone(it.dpsId.toLong())
        }
        mappingApiService.deleteByNomisPhoneId(it.nomisId)
      }
    } ?: run {
      telemetryClient.trackEvent(
        "contactperson-phone-synchronisation-deleted-ignored",
        telemetry,
      )
    }
  }

  suspend fun personEmailAdded(event: PersonInternetAddressEvent) {
    val telemetry =
      telemetryOf("nomisPersonId" to event.personId, "dpsContactId" to event.personId, "nomisInternetAddressId" to event.internetAddressId)

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-email-synchronisation-created-skipped",
        telemetry,
      )
    } else {
      mappingApiService.getByNomisEmailIdOrNull(nomisInternetAddressId = event.internetAddressId)?.also {
        telemetryClient.trackEvent(
          "contactperson-email-synchronisation-created-ignored",
          telemetry + ("dpsContactEmailId" to it.dpsId),
        )
      } ?: run {
        track("contactperson-email-synchronisation-created", telemetry) {
          nomisApiService.getPerson(nomisPersonId = event.personId).also { nomisPerson ->
            val nomisAddress = nomisPerson.emailAddresses.find { it.emailAddressId == event.internetAddressId }
              ?: throw IllegalStateException("Email ${event.internetAddressId} for person ${event.personId} not found in NOMIS")
            val dpsEmail =
              dpsApiService.createContactEmail(nomisAddress.toDpsCreateContactEmailRequest(nomisPersonId = event.personId))
                .also {
                  telemetry["dpsContactEmailId"] = it.contactEmailId
                }
            val mapping = PersonEmailMappingDto(
              nomisId = event.internetAddressId,
              dpsId = dpsEmail.contactEmailId.toString(),
              mappingType = PersonEmailMappingDto.MappingType.NOMIS_CREATED,
            )

            tryToCreateMapping(mapping, telemetry)
          }
        }
      }
    }
  }

  suspend fun personEmailUpdated(event: PersonInternetAddressEvent) {
    val telemetry =
      telemetryOf("nomisPersonId" to event.personId, "dpsContactId" to event.personId, "nomisInternetAddressId" to event.internetAddressId)

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-email-synchronisation-updated-skipped",
        telemetry,
      )
    } else {
      track("contactperson-email-synchronisation-updated", telemetry) {
        val dpsContactEmailId =
          mappingApiService.getByNomisEmailId(nomisInternetAddressId = event.internetAddressId).dpsId.also {
            telemetry["dpsContactEmailId"] = it
          }
        val nomisPerson = nomisApiService.getPerson(nomisPersonId = event.personId)
        val nomisAddress = nomisPerson.emailAddresses.find { it.emailAddressId == event.internetAddressId }
          ?: throw IllegalStateException("Email ${event.internetAddressId} for person ${event.personId} not found in NOMIS")
        dpsApiService.updateContactEmail(
          dpsContactEmailId.toLong(),
          nomisAddress.toDpsUpdateContactEmailRequest(nomisPersonId = event.personId),
        )
      }
    }
  }

  suspend fun personEmailDeleted(event: PersonInternetAddressEvent) {
    val telemetry =
      telemetryOf("nomisPersonId" to event.personId, "dpsContactId" to event.personId, "nomisInternetAddressId" to event.internetAddressId)

    mappingApiService.getByNomisEmailIdOrNull(nomisInternetAddressId = event.internetAddressId)?.also {
      track("contactperson-email-synchronisation-deleted", telemetry) {
        telemetry["dpsContactEmailId"] = it.dpsId
        dpsApiService.deleteContactEmail(contactEmailId = it.dpsId.toLong())
        mappingApiService.deleteByNomisEmailId(event.internetAddressId)
      }
    } ?: run {
      telemetryClient.trackEvent(
        "contactperson-email-synchronisation-deleted-ignored",
        telemetry,
      )
    }
  }

  suspend fun personEmploymentAdded(event: PersonEmploymentEvent) {
    val telemetry =
      telemetryOf("nomisPersonId" to event.personId, "dpsContactId" to event.personId, "nomisSequenceNumber" to event.employmentSequence)

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-employment-synchronisation-created-skipped",
        telemetry,
      )
    } else {
      mappingApiService.getByNomisEmploymentIdsOrNull(nomisPersonId = event.personId, nomisSequenceNumber = event.employmentSequence)?.also {
        telemetryClient.trackEvent(
          "contactperson-employment-synchronisation-created-ignored",
          telemetry + ("dpsContactEmploymentId" to it.dpsId),
        )
      } ?: run {
        track("contactperson-employment-synchronisation-created", telemetry) {
          nomisApiService.getPerson(nomisPersonId = event.personId).also { nomisPerson ->
            val nomisEmployment = nomisPerson.employments.find { it.sequence == event.employmentSequence }
              ?: throw IllegalStateException("Employment ${event.employmentSequence} for person ${event.personId} not found in NOMIS")
            val dpsEmployment =
              dpsApiService.createContactEmployment(nomisEmployment.toDpsCreateContactEmploymentRequest(nomisPersonId = event.personId))
                .also {
                  telemetry["dpsContactEmploymentId"] = it.employmentId
                }
            val mapping = PersonEmploymentMappingDto(
              nomisPersonId = event.personId,
              nomisSequenceNumber = event.employmentSequence,
              dpsId = dpsEmployment.employmentId.toString(),
              mappingType = PersonEmploymentMappingDto.MappingType.NOMIS_CREATED,
            )

            tryToCreateMapping(mapping, telemetry)
          }
        }
      }
    }
  }

  suspend fun personEmploymentUpdated(event: PersonEmploymentEvent) {
    val telemetry =
      telemetryOf("nomisPersonId" to event.personId, "dpsContactId" to event.personId, "nomisSequenceNumber" to event.employmentSequence)

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-employment-synchronisation-updated-skipped",
        telemetry,
      )
    } else {
      track("contactperson-employment-synchronisation-updated", telemetry) {
        val dpsContactEmploymentId = mappingApiService.getByNomisEmploymentIds(
          nomisPersonId = event.personId,
          nomisSequenceNumber = event.employmentSequence,
        ).dpsId.also {
          telemetry["dpsContactEmploymentId"] = it
        }
        val nomisPerson = nomisApiService.getPerson(nomisPersonId = event.personId)
        val nomisEmployment = nomisPerson.employments.find { it.sequence == event.employmentSequence }
          ?: throw IllegalStateException("Employment ${event.employmentSequence} for person ${event.personId} not found in NOMIS")
        dpsApiService.updateContactEmployment(
          dpsContactEmploymentId.toLong(),
          nomisEmployment.toDpsUpdateContactEmploymentRequest(nomisPersonId = event.personId),
        )
      }
    }
  }

  suspend fun personEmploymentDeleted(event: PersonEmploymentEvent) {
    val telemetry =
      telemetryOf("nomisPersonId" to event.personId, "dpsContactId" to event.personId, "nomisSequenceNumber" to event.employmentSequence)

    mappingApiService.getByNomisEmploymentIdsOrNull(
      nomisPersonId = event.personId,
      nomisSequenceNumber = event.employmentSequence,
    )?.also {
      track("contactperson-employment-synchronisation-deleted", telemetry) {
        telemetry["dpsContactEmploymentId"] = it.dpsId
        dpsApiService.deleteContactEmployment(
          it.dpsId.toLong(),
        )
        mappingApiService.deleteByNomisEmploymentIds(event.personId, event.employmentSequence)
      }
    } ?: run {
      telemetryClient.trackEvent(
        "contactperson-employment-synchronisation-deleted-ignored",
        telemetry,
      )
    }
  }

  suspend fun personIdentifierAdded(event: PersonIdentifierEvent) {
    val telemetry =
      telemetryOf("nomisPersonId" to event.personId, "dpsContactId" to event.personId, "nomisSequenceNumber" to event.identifierSequence)

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-identifier-synchronisation-created-skipped",
        telemetry,
      )
    } else {
      mappingApiService.getByNomisIdentifierIdsOrNull(nomisPersonId = event.personId, nomisSequenceNumber = event.identifierSequence)?.also {
        telemetryClient.trackEvent(
          "contactperson-identifier-synchronisation-created-ignored",
          telemetry + ("dpsContactIdentityId" to it.dpsId),
        )
      } ?: run {
        track("contactperson-identifier-synchronisation-created", telemetry) {
          nomisApiService.getPerson(nomisPersonId = event.personId).also { nomisPerson ->
            val nomisIdentifier = nomisPerson.identifiers.find { it.sequence == event.identifierSequence } ?: throw IllegalStateException("Identifier ${event.identifierSequence} for person ${event.personId} not found in NOMIS")
            val dpsIdentity = dpsApiService.createContactIdentity(nomisIdentifier.toDpsCreateContactIdentityRequest(nomisPersonId = event.personId)).also {
              telemetry["dpsContactIdentityId"] = it.contactIdentityId
            }
            val mapping = PersonIdentifierMappingDto(
              nomisPersonId = event.personId,
              nomisSequenceNumber = event.identifierSequence,
              dpsId = dpsIdentity.contactIdentityId.toString(),
              mappingType = PersonIdentifierMappingDto.MappingType.NOMIS_CREATED,
            )

            tryToCreateMapping(mapping, telemetry)
          }
        }
      }
    }
  }

  suspend fun personIdentifierUpdated(event: PersonIdentifierEvent) {
    val telemetry =
      telemetryOf("nomisPersonId" to event.personId, "dpsContactId" to event.personId, "nomisSequenceNumber" to event.identifierSequence)

    if (event.doesOriginateInDps()) {
      telemetryClient.trackEvent(
        "contactperson-identifier-synchronisation-updated-skipped",
        telemetry,
      )
    } else {
      track("contactperson-identifier-synchronisation-updated", telemetry) {
        val dpsContactIdentityId = mappingApiService.getByNomisIdentifierIds(
          nomisPersonId = event.personId,
          nomisSequenceNumber = event.identifierSequence,
        ).dpsId.also {
          telemetry["dpsContactIdentityId"] = it
        }
        val nomisPerson = nomisApiService.getPerson(nomisPersonId = event.personId)
        val nomisIdentifier = nomisPerson.identifiers.find { it.sequence == event.identifierSequence }
          ?: throw IllegalStateException("Identifier ${event.identifierSequence} for person ${event.personId} not found in NOMIS")
        dpsApiService.updateContactIdentity(
          dpsContactIdentityId.toLong(),
          nomisIdentifier.toDpsUpdateContactIdentityRequest(nomisPersonId = event.personId),
        )
      }
    }
  }

  suspend fun personIdentifierDeleted(event: PersonIdentifierEvent) {
    val telemetry =
      telemetryOf("nomisPersonId" to event.personId, "dpsContactId" to event.personId, "nomisSequenceNumber" to event.identifierSequence)

    mappingApiService.getByNomisIdentifierIdsOrNull(
      nomisPersonId = event.personId,
      nomisSequenceNumber = event.identifierSequence,
    )?.also {
      track("contactperson-identifier-synchronisation-deleted", telemetry) {
        telemetry["dpsContactIdentityId"] = it.dpsId
        dpsApiService.deleteContactIdentity(
          it.dpsId.toLong(),
        )
        mappingApiService.deleteByNomisIdentifierIds(event.personId, event.identifierSequence)
      }
    } ?: run {
      telemetryClient.trackEvent(
        "contactperson-identifier-synchronisation-deleted-ignored",
        telemetry,
      )
    }
  }

  suspend fun prisonerMerged(prisonerMergeEvent: PrisonerMergeDomainEvent) {
    val retainedOffenderNumber = prisonerMergeEvent.additionalInformation.nomsNumber
    val removedOffenderNumber = prisonerMergeEvent.additionalInformation.removedNomsNumber
    val telemetry = mutableMapOf(
      "offenderNo" to retainedOffenderNumber,
      "bookingId" to prisonerMergeEvent.additionalInformation.bookingId,
      "removedOffenderNo" to removedOffenderNumber,
    )

    val nomisContacts = nomisApiService.getContactsForPrisoner(retainedOffenderNumber).contacts.also {
      telemetry["contactsCount"] = it.size
    }
    val dpsChangedResponse = dpsApiService.replaceMergedPrisonerContacts(
      MergePrisonerContactRequest(
        prisonerContacts = nomisContacts.map { it.toDpsSyncPrisonerRelationship(retainedOffenderNumber) },
        removedPrisonerNumber = removedOffenderNumber,
        retainedPrisonerNumber = retainedOffenderNumber,
      ),
    )

    tryToReplacePrisonerMergedMappings(
      offenderNo = retainedOffenderNumber,
      mapping = ContactPersonPrisonerMappingsDto(
        mappingType = ContactPersonPrisonerMappingsDto.MappingType.NOMIS_CREATED,
        personContactMapping = dpsChangedResponse.relationshipsCreated.map { ContactPersonSimpleMappingIdDto(nomisId = it.relationship.nomisId, dpsId = "${it.relationship.dpsId}") },
        personContactRestrictionMapping = dpsChangedResponse.relationshipsCreated.flatMap { it.restrictions.map { restriction -> ContactPersonSimpleMappingIdDto(nomisId = restriction.nomisId, dpsId = "${restriction.dpsId}") } },
        personContactMappingsToRemoveByDpsId = dpsChangedResponse.relationshipsRemoved.map { "${it.prisonerContactId}" },
        personContactRestrictionMappingsToRemoveByDpsId = dpsChangedResponse.relationshipsRemoved.flatMap { contact -> contact.prisonerContactRestrictionIds.map { "$it" } },
      ),
      telemetry = telemetry,
    )
  }
  suspend fun prisonerBookingMoved(prisonerBookingMovedEvent: PrisonerBookingMovedDomainEvent) {
    log.info("TODO: prisonerBookingMoved {}", prisonerBookingMovedEvent)
  }
  suspend fun resetPrisonerContactsForAdmission(prisonerReceivedEvent: PrisonerReceiveDomainEvent) {
    when (prisonerReceivedEvent.additionalInformation.reason) {
      "READMISSION_SWITCH_BOOKING", "NEW_ADMISSION" -> {
        val offenderNo = prisonerReceivedEvent.additionalInformation.nomsNumber
        val telemetry = mutableMapOf<String, Any>(
          "offenderNo" to offenderNo,
        )

        val nomisContacts = nomisApiService.getContactsForPrisoner(offenderNo).contacts.also {
          telemetry["contactsCount"] = it.size
        }
        val dpsChangedResponse = dpsApiService.resetPrisonerContacts(
          ResetPrisonerContactRequest(
            prisonerContacts = nomisContacts.map { it.toDpsSyncPrisonerRelationship(offenderNo) },
            prisonerNumber = offenderNo,
          ),
        )
        tryToReplaceBookingChangedMappings(
          offenderNo = offenderNo,
          mapping = ContactPersonPrisonerMappingsDto(
            mappingType = ContactPersonPrisonerMappingsDto.MappingType.NOMIS_CREATED,
            personContactMapping = dpsChangedResponse.relationshipsCreated.map { ContactPersonSimpleMappingIdDto(nomisId = it.relationship.nomisId, dpsId = "${it.relationship.dpsId}") },
            personContactRestrictionMapping = dpsChangedResponse.relationshipsCreated.flatMap { it.restrictions.map { restriction -> ContactPersonSimpleMappingIdDto(nomisId = restriction.nomisId, dpsId = "${restriction.dpsId}") } },
            personContactMappingsToRemoveByDpsId = dpsChangedResponse.relationshipsRemoved.map { "${it.prisonerContactId}" },
            personContactRestrictionMappingsToRemoveByDpsId = dpsChangedResponse.relationshipsRemoved.flatMap { contact -> contact.prisonerContactRestrictionIds.map { "$it" } },
          ),
          telemetry = telemetry,
        )
      }
    }
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
        synchronisationType = SynchronisationType.PERSONALRELATIONSHIPS,
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
        synchronisationType = SynchronisationType.PERSONALRELATIONSHIPS,
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
        synchronisationType = SynchronisationType.PERSONALRELATIONSHIPS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }
  private suspend fun tryToCreateMapping(
    mapping: PersonPhoneMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createPhoneMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for phone id $mapping", e)
      queueService.sendMessage(
        messageType = ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_PHONE_MAPPING.name,
        synchronisationType = SynchronisationType.PERSONALRELATIONSHIPS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }
  private suspend fun tryToCreateMapping(
    mapping: PersonEmailMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createEmailMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for email id $mapping", e)
      queueService.sendMessage(
        messageType = ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_EMAIL_MAPPING.name,
        synchronisationType = SynchronisationType.PERSONALRELATIONSHIPS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }
  private suspend fun tryToCreateMapping(
    mapping: PersonIdentifierMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createIdentifierMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for identifier id $mapping", e)
      queueService.sendMessage(
        messageType = ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_IDENTIFIER_MAPPING.name,
        synchronisationType = SynchronisationType.PERSONALRELATIONSHIPS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }
  private suspend fun tryToCreateMapping(
    mapping: PersonEmploymentMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createEmploymentMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for employment id $mapping", e)
      queueService.sendMessage(
        messageType = ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_EMPLOYMENT_MAPPING.name,
        synchronisationType = SynchronisationType.PERSONALRELATIONSHIPS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }
  private suspend fun tryToCreateMapping(
    mapping: PersonContactRestrictionMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createContactRestrictionMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for contact restriction id $mapping", e)
      queueService.sendMessage(
        messageType = ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_CONTACT_RESTRICTION_MAPPING.name,
        synchronisationType = SynchronisationType.PERSONALRELATIONSHIPS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }
  private suspend fun tryToCreateMapping(
    mapping: PersonRestrictionMappingDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      createPersonRestrictionMapping(mapping)
    } catch (e: Exception) {
      log.error("Failed to create mapping for person restriction id $mapping", e)
      queueService.sendMessage(
        messageType = ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_PERSON_RESTRICTION_MAPPING.name,
        synchronisationType = SynchronisationType.PERSONALRELATIONSHIPS,
        message = mapping,
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }

  private suspend fun tryToReplacePrisonerMergedMappings(
    offenderNo: String,
    mapping: ContactPersonPrisonerMappingsDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      mappingApiService.replaceMappingsForPrisoner(offenderNo, mapping)
      telemetryClient.trackEvent(
        "from-nomis-synch-contactperson-merge",
        telemetry,
      )
    } catch (e: Exception) {
      log.error("Failed to replace prisoner person for $mapping", e)
      queueService.sendMessage(
        messageType = RETRY_REPLACE_PRISONER_PERSON_PRISONER_MERGED_MAPPINGS.name,
        synchronisationType = SynchronisationType.PERSONALRELATIONSHIPS,
        message = ContactPersonPrisonerMappings(offenderNo = offenderNo, mappings = mapping),
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }
  private suspend fun tryToReplaceBookingChangedMappings(
    offenderNo: String,
    mapping: ContactPersonPrisonerMappingsDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      mappingApiService.replaceMappingsForPrisoner(offenderNo, mapping)
      telemetryClient.trackEvent(
        "from-nomis-synch-contactperson-booking-changed",
        telemetry,
      )
    } catch (e: Exception) {
      log.error("Failed to replace prisoner person for $mapping", e)
      queueService.sendMessage(
        messageType = RETRY_REPLACE_PRISONER_PERSON_BOOKING_CHANGED_MAPPINGS.name,
        synchronisationType = SynchronisationType.PERSONALRELATIONSHIPS,
        message = ContactPersonPrisonerMappings(offenderNo = offenderNo, mappings = mapping),
        telemetryAttributes = telemetry.valuesAsStrings(),
      )
    }
  }
  private suspend fun tryToReplaceBookingMovedMappings(
    offenderNo: String,
    mapping: ContactPersonPrisonerMappingsDto,
    telemetry: Map<String, Any>,
  ) {
    try {
      mappingApiService.replaceMappingsForPrisoner(offenderNo, mapping)
      telemetryClient.trackEvent(
        "from-nomis-synch-contactperson-booking-moved",
        telemetry,
      )
    } catch (e: Exception) {
      log.error("Failed to replace prisoner person for $mapping", e)
      queueService.sendMessage(
        messageType = RETRY_REPLACE_PRISONER_PERSON_BOOKING_MOVED_MAPPINGS.name,
        synchronisationType = SynchronisationType.PERSONALRELATIONSHIPS,
        message = ContactPersonPrisonerMappings(offenderNo = offenderNo, mappings = mapping),
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
  private suspend fun createPhoneMapping(
    mapping: PersonPhoneMappingDto,
  ) {
    mappingApiService.createPhoneMapping(mapping).takeIf { it.isError }?.also {
      with(it.errorResponse!!.moreInfo) {
        telemetryClient.trackEvent(
          "from-nomis-sync-contactperson-duplicate",
          mapOf(
            "existingNomisPhoneId" to existing.nomisId,
            "existingDpsContactPhoneId" to existing.dpsId,
            "duplicateNomisPhoneId" to duplicate.nomisId,
            "duplicateDpsContactPhoneId" to duplicate.dpsId,
            "type" to "PHONE",
          ),
        )
      }
    }
  }
  private suspend fun createEmailMapping(
    mapping: PersonEmailMappingDto,
  ) {
    mappingApiService.createEmailMapping(mapping).takeIf { it.isError }?.also {
      with(it.errorResponse!!.moreInfo) {
        telemetryClient.trackEvent(
          "from-nomis-sync-contactperson-duplicate",
          mapOf(
            "existingNomisInternetAddressId" to existing.nomisId,
            "existingDpsContactEmailId" to existing.dpsId,
            "duplicateNomisInternetAddressId" to duplicate.nomisId,
            "duplicateDpsContactEmailId" to duplicate.dpsId,
            "type" to "EMAIL",
          ),
        )
      }
    }
  }
  private suspend fun createIdentifierMapping(
    mapping: PersonIdentifierMappingDto,
  ) {
    mappingApiService.createIdentifierMapping(mapping).takeIf { it.isError }?.also {
      with(it.errorResponse!!.moreInfo) {
        telemetryClient.trackEvent(
          "from-nomis-sync-contactperson-duplicate",
          mapOf(
            "existingNomisPersonId" to existing.nomisPersonId,
            "existingNomisSequenceNumber" to existing.nomisSequenceNumber,
            "existingDpsContactIdentityId" to existing.dpsId,
            "duplicateNomisPersonId" to duplicate.nomisPersonId,
            "duplicateNomisSequenceNumber" to duplicate.nomisSequenceNumber,
            "duplicateDpsContactIdentityId" to duplicate.dpsId,
            "type" to "IDENTIFIER",
          ),
        )
      }
    }
  }
  private suspend fun createEmploymentMapping(
    mapping: PersonEmploymentMappingDto,
  ) {
    mappingApiService.createEmploymentMapping(mapping).takeIf { it.isError }?.also {
      with(it.errorResponse!!.moreInfo) {
        telemetryClient.trackEvent(
          "from-nomis-sync-contactperson-duplicate",
          mapOf(
            "existingNomisPersonId" to existing.nomisPersonId,
            "existingNomisSequenceNumber" to existing.nomisSequenceNumber,
            "existingDpsContactIdentityId" to existing.dpsId,
            "duplicateNomisPersonId" to duplicate.nomisPersonId,
            "duplicateNomisSequenceNumber" to duplicate.nomisSequenceNumber,
            "duplicateDpsContactIdentityId" to duplicate.dpsId,
            "type" to "EMPLOYMENT",
          ),
        )
      }
    }
  }
  private suspend fun createContactRestrictionMapping(
    mapping: PersonContactRestrictionMappingDto,
  ) {
    mappingApiService.createContactRestrictionMapping(mapping).takeIf { it.isError }?.also {
      with(it.errorResponse!!.moreInfo) {
        telemetryClient.trackEvent(
          "from-nomis-sync-contactperson-duplicate",
          mapOf(
            "existingNomisContactRestrictionId" to existing.nomisId,
            "existingDpsPrisonerContactRestrictionId" to existing.dpsId,
            "duplicateNomisContactRestrictionId" to duplicate.nomisId,
            "duplicateDpsPrisonerContactRestrictionId" to duplicate.dpsId,
            "type" to "CONTACT_RESTRICTION",
          ),
        )
      }
    }
  }
  private suspend fun createPersonRestrictionMapping(
    mapping: PersonRestrictionMappingDto,
  ) {
    mappingApiService.createPersonRestrictionMapping(mapping).takeIf { it.isError }?.also {
      with(it.errorResponse!!.moreInfo) {
        telemetryClient.trackEvent(
          "from-nomis-sync-contactperson-duplicate",
          mapOf(
            "existingNomisPersonRestrictionId" to existing.nomisId,
            "existingDpsContactRestrictionId" to existing.dpsId,
            "duplicateNomisPersonRestrictionId" to duplicate.nomisId,
            "duplicateDpsContactRestrictionId" to duplicate.dpsId,
            "type" to "PERSON_RESTRICTION",
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
  suspend fun retryCreateEmailMapping(retryMessage: InternalMessage<PersonEmailMappingDto>) {
    createEmailMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent(
          "contactperson-email-mapping-synchronisation-created",
          retryMessage.telemetryAttributes,
        )
      }
  }
  suspend fun retryCreatePhoneMapping(retryMessage: InternalMessage<PersonPhoneMappingDto>) {
    createPhoneMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent(
          "contactperson-phone-mapping-synchronisation-created",
          retryMessage.telemetryAttributes,
        )
      }
  }
  suspend fun retryCreateIdentifierMapping(retryMessage: InternalMessage<PersonIdentifierMappingDto>) {
    createIdentifierMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent(
          "contactperson-identifier-mapping-synchronisation-created",
          retryMessage.telemetryAttributes,
        )
      }
  }
  suspend fun retryCreateEmploymentMapping(retryMessage: InternalMessage<PersonEmploymentMappingDto>) {
    createEmploymentMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent(
          "contactperson-employment-mapping-synchronisation-created",
          retryMessage.telemetryAttributes,
        )
      }
  }
  suspend fun retryCreateContactRestrictionMapping(retryMessage: InternalMessage<PersonContactRestrictionMappingDto>) {
    createContactRestrictionMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent(
          "contactperson-contact-restriction-mapping-synchronisation-created",
          retryMessage.telemetryAttributes,
        )
      }
  }
  suspend fun retryCreatePersonRestrictionMapping(retryMessage: InternalMessage<PersonRestrictionMappingDto>) {
    createPersonRestrictionMapping(retryMessage.body)
      .also {
        telemetryClient.trackEvent(
          "contactperson-person-restriction-mapping-synchronisation-created",
          retryMessage.telemetryAttributes,
        )
      }
  }

  suspend fun retryReplacePrisonerPersonPrisonerMergedMappings(retryMessage: InternalMessage<ContactPersonPrisonerMappings>) {
    mappingApiService.replaceMappingsForPrisoner(retryMessage.body.offenderNo, retryMessage.body.mappings)
      .also {
        telemetryClient.trackEvent(
          "from-nomis-synch-contactperson-merge",
          retryMessage.telemetryAttributes,
        )
      }
  }
  suspend fun retryReplacePrisonerPersonBookingChangeMappings(retryMessage: InternalMessage<ContactPersonPrisonerMappings>) {
    mappingApiService.replaceMappingsForPrisoner(retryMessage.body.offenderNo, retryMessage.body.mappings)
      .also {
        telemetryClient.trackEvent(
          "from-nomis-synch-contactperson-booking-changed",
          retryMessage.telemetryAttributes,
        )
      }
  }
  suspend fun retryReplacePrisonerPersonBookingMovedMappings(retryMessage: InternalMessage<ContactPersonPrisonerMappings>) {
    mappingApiService.replaceMappingsForPrisoner(retryMessage.body.offenderNo, retryMessage.body.mappings)
      .also {
        telemetryClient.trackEvent(
          "from-nomis-synch-contactperson-booking-moved",
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
  createdTime = this.audit.createDatetime,
)
fun ContactPerson.toDpsUpdateContactRequest() = SyncUpdateContactRequest(
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
  updatedBy = this.audit.modifyUserId!!,
  updatedTime = this.audit.modifyDatetime!!,
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
  createdTime = this.audit.createDatetime,
)

fun PrisonerContact.toDpsSyncPrisonerRelationship(offenderNo: String) = SyncPrisonerRelationship(
  id = this.id,
  restrictions = this.restrictions.map { restriction ->
    SyncRelationshipRestriction(
      id = restriction.id,
      restrictionType = restriction.type.toCodedValue(),
      startDate = restriction.effectiveDate,
      expiryDate = restriction.expiryDate,
      comment = restriction.comment,
      createDateTime = restriction.audit.createDatetime,
      createUsername = if (restriction.audit.hasBeenModified()) {
        restriction.audit.createUsername
      } else {
        restriction.enteredStaff.username
      },
      modifyDateTime = restriction.audit.modifyDatetime,
      modifyUsername = if (restriction.audit.hasBeenModified()) {
        restriction.enteredStaff.username
      } else {
        restriction.audit.modifyUserId
      },
    )
  },
  contactId = this.person.personId,
  contactType = this.contactType.toCodedValue(),
  relationshipType = this.relationshipType.toCodedValue(),
  active = this.active,
  currentTerm = this.bookingSequence == 1L,
  nextOfKin = this.nextOfKin,
  emergencyContact = this.emergencyContact,
  approvedVisitor = this.approvedVisitor,
  comment = this.comment,
  expiryDate = this.expiryDate,
  createDateTime = this.audit.createDatetime,
  createUsername = this.audit.createUsername,
  modifyDateTime = this.audit.modifyDatetime,
  modifyUsername = this.audit.modifyUserId,
  prisonerNumber = offenderNo,
)

private fun CodeDescription.toCodedValue() = CodedValue(code = this.code, description = this.description)
private fun NomisAudit.hasBeenModified() = this.modifyUserId != null

fun PersonContact.toDpsUpdatePrisonerContactRequest(nomisPersonId: Long) = SyncUpdatePrisonerContactRequest(
  // these will be mutable in DPS but we still supply them
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
  updatedBy = this.audit.modifyUserId,
  updatedTime = this.audit.modifyDatetime!!,
)

fun PersonAddress.toDpsCreateContactAddressRequest(nomisPersonId: Long) = SyncCreateContactAddressRequest(
  contactId = nomisPersonId,
  addressType = this.type?.code,
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
  createdTime = this.audit.createDatetime,
)
fun PersonAddress.toDpsUpdateContactAddressRequest(nomisPersonId: Long) = SyncUpdateContactAddressRequest(
  contactId = nomisPersonId,
  addressType = this.type?.code,
  primaryAddress = this.primaryAddress,
  flat = this.flat,
  property = this.premise,
  street = this.street,
  area = this.locality,
  cityCode = this.city?.code,
  countyCode = this.county?.code,
  countryCode = this.country?.code,
  postcode = this.postcode,
  verified = this.validatedPAF,
  mailFlag = this.mailAddress,
  startDate = this.startDate,
  endDate = this.endDate,
  noFixedAddress = this.noFixedAddress,
  comments = this.comment,
  updatedBy = this.audit.modifyUserId!!,
  updatedTime = this.audit.modifyDatetime!!,
)

fun PersonEmailAddress.toDpsCreateContactEmailRequest(nomisPersonId: Long) = SyncCreateContactEmailRequest(
  contactId = nomisPersonId,
  createdBy = this.audit.createUsername,
  emailAddress = this.email,
  createdTime = this.audit.createDatetime,
)
fun PersonEmailAddress.toDpsUpdateContactEmailRequest(nomisPersonId: Long) = SyncUpdateContactEmailRequest(
  contactId = nomisPersonId,
  updatedBy = this.audit.modifyUserId!!,
  emailAddress = this.email,
  updatedTime = this.audit.modifyDatetime!!,
)
fun PersonPhoneNumber.toDpsCreateContactPhoneRequest(nomisPersonId: Long) = SyncCreateContactPhoneRequest(
  contactId = nomisPersonId,
  createdBy = this.audit.createUsername,
  phoneNumber = this.number,
  extNumber = this.extension,
  phoneType = this.type.code,
  createdTime = this.audit.createDatetime,
)
fun PersonPhoneNumber.toDpsUpdateContactPhoneRequest(nomisPersonId: Long) = SyncUpdateContactPhoneRequest(
  contactId = nomisPersonId,
  updatedBy = this.audit.modifyUserId!!,
  phoneNumber = this.number,
  extNumber = this.extension,
  phoneType = this.type.code,
  updatedTime = this.audit.modifyDatetime!!,
)
fun PersonPhoneNumber.toDpsCreateContactAddressPhoneRequest(dpsAddressId: Long) = SyncCreateContactAddressPhoneRequest(
  contactAddressId = dpsAddressId,
  createdBy = this.audit.createUsername,
  phoneNumber = this.number,
  extNumber = this.extension,
  phoneType = this.type.code,
  createdTime = this.audit.createDatetime,
)
fun PersonPhoneNumber.toDpsUpdateContactAddressPhoneRequest() = SyncUpdateContactAddressPhoneRequest(
  updatedBy = this.audit.modifyUserId!!,
  phoneNumber = this.number,
  extNumber = this.extension,
  phoneType = this.type.code,
  updatedTime = this.audit.modifyDatetime!!,
)
fun PersonIdentifier.toDpsCreateContactIdentityRequest(nomisPersonId: Long) = SyncCreateContactIdentityRequest(
  contactId = nomisPersonId,
  createdBy = this.audit.createUsername,
  identityValue = this.identifier,
  identityType = this.type.code,
  issuingAuthority = this.issuedAuthority,
  createdTime = this.audit.createDatetime,
)
fun PersonIdentifier.toDpsUpdateContactIdentityRequest(nomisPersonId: Long) = SyncUpdateContactIdentityRequest(
  contactId = nomisPersonId,
  updatedBy = this.audit.modifyUserId!!,
  identityValue = this.identifier,
  identityType = this.type.code,
  issuingAuthority = this.issuedAuthority,
  updatedTime = this.audit.modifyDatetime!!,
)

fun PersonEmployment.toDpsCreateContactEmploymentRequest(nomisPersonId: Long) = SyncCreateEmploymentRequest(
  contactId = nomisPersonId,
  organisationId = this.corporate.id,
  active = this.active,
  createdBy = this.audit.createUsername,
  createdTime = this.audit.createDatetime,
)

fun PersonEmployment.toDpsUpdateContactEmploymentRequest(nomisPersonId: Long) = SyncUpdateEmploymentRequest(
  contactId = nomisPersonId,
  organisationId = this.corporate.id,
  active = this.active,
  updatedBy = this.audit.modifyUserId!!,
  updatedTime = this.audit.modifyDatetime!!,
)

fun ContactRestriction.toDpsCreatePrisonerContactRestrictionRequest(dpsPrisonerContactId: Long) = SyncCreatePrisonerContactRestrictionRequest(
  prisonerContactId = dpsPrisonerContactId,
  restrictionType = this.type.code,
  comments = this.comment,
  startDate = this.effectiveDate,
  expiryDate = this.expiryDate,
  // DPS use the enteredBy username since they use the createdBy as business data as well as audit
  createdBy = this.enteredStaff.username,
  createdTime = this.audit.createDatetime,
)

fun ContactRestriction.toDpsCreateContactRestrictionRequest(dpsContactId: Long) = SyncCreateContactRestrictionRequest(
  contactId = dpsContactId,
  restrictionType = this.type.code,
  comments = this.comment,
  startDate = this.effectiveDate,
  expiryDate = this.expiryDate,
  // DPS use the enteredBy username since they use the createdBy as business data as well as audit
  createdBy = this.enteredStaff.username,
  createdTime = this.audit.createDatetime,
)

fun ContactRestriction.toDpsUpdateContactRestrictionRequest(dpsContactId: Long) = SyncUpdateContactRestrictionRequest(
  contactId = dpsContactId,
  restrictionType = this.type.code,
  comments = this.comment,
  startDate = this.effectiveDate,
  expiryDate = this.expiryDate,
  // DPS use the enteredBy username since they use the updatedBy as business data as well as audit
  updatedBy = this.enteredStaff.username,
  updatedTime = this.audit.modifyDatetime ?: LocalDateTime.now(),
)

fun ContactRestriction.toDpsUpdatePrisonerContactRestrictionRequest() = SyncUpdatePrisonerContactRestrictionRequest(
  restrictionType = this.type.code,
  comments = this.comment,
  startDate = this.effectiveDate,
  expiryDate = this.expiryDate,
  // DPS use the enteredBy username since they use the updatedBy as business data as well as audit
  updatedBy = this.enteredStaff.username,
  updatedTime = this.audit.modifyDatetime ?: LocalDateTime.now(),
)
