package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateContactResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncContactAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncContactAddressPhone
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncContactEmail
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncContactIdentity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncContactPhone
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncContactRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactAddressPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactEmailRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactIdentityRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactPhoneRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreatePrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreatePrisonerContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncPrisonerContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncPrisonerContactRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncUpdateContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncUpdatePrisonerContactRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest

@Service
class ContactPersonDpsApiService(@Qualifier("contactPersonApiWebClient") private val webClient: WebClient) {
  suspend fun migrateContact(contact: MigrateContactRequest): MigrateContactResponse = webClient.post()
    .uri("/migrate/contact")
    .bodyValue(contact)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun createContact(contact: SyncCreateContactRequest): SyncContact = webClient.post()
    .uri("/sync/contact")
    .bodyValue(contact)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun createPrisonerContact(prisonerContact: SyncCreatePrisonerContactRequest): SyncPrisonerContact = webClient.post()
    .uri("/sync/prisoner-contact")
    .bodyValue(prisonerContact)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun createContactAddress(contactAddress: SyncCreateContactAddressRequest): SyncContactAddress = webClient.post()
    .uri("/sync/contact-address")
    .bodyValue(contactAddress)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun createContactEmail(contactEmail: SyncCreateContactEmailRequest): SyncContactEmail = webClient.post()
    .uri("/sync/contact-email")
    .bodyValue(contactEmail)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun createContactPhone(contactPhone: SyncCreateContactPhoneRequest): SyncContactPhone = webClient.post()
    .uri("/sync/contact-phone")
    .bodyValue(contactPhone)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun createContactAddressPhone(contactAddressPhone: SyncCreateContactAddressPhoneRequest): SyncContactAddressPhone = webClient.post()
    .uri("/sync/contact-address-phone")
    .bodyValue(contactAddressPhone)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun createContactIdentity(contactIdentity: SyncCreateContactIdentityRequest): SyncContactIdentity = webClient.post()
    .uri("/sync/contact-identity")
    .bodyValue(contactIdentity)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun createPrisonerContactRestriction(contactRestriction: SyncCreatePrisonerContactRestrictionRequest): SyncPrisonerContactRestriction = webClient.post()
    .uri("/sync/prisoner-contact-restriction")
    .bodyValue(contactRestriction)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updatePrisonerContactRestriction(prisonerContactRestrictionId: Long, contactRestriction: SyncUpdatePrisonerContactRestrictionRequest): SyncPrisonerContactRestriction = webClient.put()
    .uri("/sync/prisoner-contact-restriction/{prisonerContactRestrictionId}", prisonerContactRestrictionId)
    .bodyValue(contactRestriction)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun createContactRestriction(contactRestriction: SyncCreateContactRestrictionRequest): SyncContactRestriction = webClient.post()
    .uri("/sync/contact-restriction")
    .bodyValue(contactRestriction)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updateContactRestriction(contactRestrictionId: Long, contactRestriction: SyncUpdateContactRestrictionRequest): SyncContactRestriction = webClient.put()
    .uri("/sync/contact-restriction/{contactRestrictionId}", contactRestrictionId)
    .bodyValue(contactRestriction)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
