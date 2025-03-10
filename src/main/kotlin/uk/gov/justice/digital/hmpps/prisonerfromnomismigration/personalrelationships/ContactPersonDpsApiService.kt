package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityIgnoreNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.CodedValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.ContactsAndRestrictions
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MigrateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MigrateContactResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.MigratePrisonerContactRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncContactAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncContactAddressPhone
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncContactEmail
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncContactIdentity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncContactPhone
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncContactRestriction
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncEmployment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncPrisonerContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.model.SyncPrisonerContactRestriction
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

@Service
class ContactPersonDpsApiService(@Qualifier("personalRelationshipsApiWebClient") private val webClient: WebClient) {
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

  suspend fun updateContact(contactId: Long, contact: SyncUpdateContactRequest): SyncContact = webClient.put()
    .uri("/sync/contact/{contactId}", contactId)
    .bodyValue(contact)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteContact(contactId: Long) {
    webClient.delete()
      .uri("/sync/contact/{contactId}", contactId)
      .retrieve()
      .awaitBodilessEntityIgnoreNotFound()
  }

  suspend fun createPrisonerContact(prisonerContact: SyncCreatePrisonerContactRequest): SyncPrisonerContact = webClient.post()
    .uri("/sync/prisoner-contact")
    .bodyValue(prisonerContact)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updatePrisonerContact(prisonerContactId: Long, prisonerContact: SyncUpdatePrisonerContactRequest) {
    webClient.put()
      .uri("/sync/prisoner-contact/{prisonerContactId}", prisonerContactId)
      .bodyValue(prisonerContact)
      .retrieve()
      .awaitBodilessEntityOrLogAndRethrowBadRequest()
  }

  suspend fun deletePrisonerContact(prisonerContactId: Long) {
    webClient.delete()
      .uri("/sync/prisoner-contact/{prisonerContactId}", prisonerContactId)
      .retrieve()
      .awaitBodilessEntityIgnoreNotFound()
  }

  suspend fun createContactAddress(contactAddress: SyncCreateContactAddressRequest): SyncContactAddress = webClient.post()
    .uri("/sync/contact-address")
    .bodyValue(contactAddress)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updateContactAddress(addressId: Long, contactAddress: SyncUpdateContactAddressRequest) {
    webClient.put()
      .uri("/sync/contact-address/{addressId}", addressId)
      .bodyValue(contactAddress)
      .retrieve()
      .awaitBodilessEntityOrLogAndRethrowBadRequest()
  }
  suspend fun deleteContactAddress(addressId: Long) {
    webClient.delete()
      .uri("/sync/contact-address/{addressId}", addressId)
      .retrieve()
      .awaitBodilessEntityIgnoreNotFound()
  }

  suspend fun createContactEmail(contactEmail: SyncCreateContactEmailRequest): SyncContactEmail = webClient.post()
    .uri("/sync/contact-email")
    .bodyValue(contactEmail)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updateContactEmail(contactEmailId: Long, contactEmail: SyncUpdateContactEmailRequest): SyncContactEmail = webClient.put()
    .uri("/sync/contact-email/{contactEmailId}", contactEmailId)
    .bodyValue(contactEmail)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteContactEmail(contactEmailId: Long) {
    webClient.delete()
      .uri("/sync/contact-email/{contactEmailId}", contactEmailId)
      .retrieve()
      .awaitBodilessEntityIgnoreNotFound()
  }

  suspend fun createContactEmployment(contactEmployment: SyncCreateEmploymentRequest): SyncEmployment = webClient.post()
    .uri("/sync/employment")
    .bodyValue(contactEmployment)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updateContactEmployment(contactEmploymentId: Long, contactEmployment: SyncUpdateEmploymentRequest): SyncEmployment = webClient.put()
    .uri("/sync/employment/{contactEmploymentId}", contactEmploymentId)
    .bodyValue(contactEmployment)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteContactEmployment(contactEmploymentId: Long) {
    webClient.delete()
      .uri("/sync/employment/{contactEmploymentId}", contactEmploymentId)
      .retrieve()
      .awaitBodilessEntityIgnoreNotFound()
  }

  suspend fun createContactPhone(contactPhone: SyncCreateContactPhoneRequest): SyncContactPhone = webClient.post()
    .uri("/sync/contact-phone")
    .bodyValue(contactPhone)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updateContactPhone(contactPhoneId: Long, contactPhone: SyncUpdateContactPhoneRequest): SyncContactPhone = webClient.put()
    .uri("/sync/contact-phone/{contactPhoneId}", contactPhoneId)
    .bodyValue(contactPhone)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteContactPhone(contactPhoneId: Long) {
    webClient.delete()
      .uri("/sync/contact-phone/{contactPhoneId}", contactPhoneId)
      .retrieve()
      .awaitBodilessEntityIgnoreNotFound()
  }

  suspend fun createContactAddressPhone(contactAddressPhone: SyncCreateContactAddressPhoneRequest): SyncContactAddressPhone = webClient.post()
    .uri("/sync/contact-address-phone")
    .bodyValue(contactAddressPhone)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updateContactAddressPhone(contactAddressPhoneId: Long, contactAddressPhone: SyncUpdateContactAddressPhoneRequest): SyncContactAddressPhone = webClient.put()
    .uri("/sync/contact-address-phone/{contactAddressPhoneId}", contactAddressPhoneId)
    .bodyValue(contactAddressPhone)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteContactAddressPhone(contactAddressPhoneId: Long) {
    webClient.delete()
      .uri("/sync/contact-address-phone/{contactAddressPhoneId}", contactAddressPhoneId)
      .retrieve()
      .awaitBodilessEntityIgnoreNotFound()
  }

  suspend fun createContactIdentity(contactIdentity: SyncCreateContactIdentityRequest): SyncContactIdentity = webClient.post()
    .uri("/sync/contact-identity")
    .bodyValue(contactIdentity)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updateContactIdentity(contactIdentityId: Long, contactIdentity: SyncUpdateContactIdentityRequest): SyncContactIdentity = webClient.put()
    .uri("/sync/contact-identity/{contactIdentityId}", contactIdentityId)
    .bodyValue(contactIdentity)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteContactIdentity(contactIdentityId: Long) {
    webClient.delete()
      .uri("/sync/contact-identity/{contactIdentityId}", contactIdentityId)
      .retrieve()
      .awaitBodilessEntityIgnoreNotFound()
  }

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

  suspend fun deletePrisonerContactRestriction(prisonerContactRestrictionId: Long) {
    webClient.delete()
      .uri("/sync/prisoner-contact-restriction/{prisonerContactRestrictionId}", prisonerContactRestrictionId)
      .retrieve()
      .awaitBodilessEntityIgnoreNotFound()
  }

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

  suspend fun deleteContactRestriction(contactRestrictionId: Long) {
    webClient.delete()
      .uri("/sync/contact-restriction/{contactRestrictionId}", contactRestrictionId)
      .retrieve()
      .awaitBodilessEntityIgnoreNotFound()
  }

  suspend fun replaceMergedPrisonerContacts(prisonerNumber: String, mergePrisonerContactRequest: MergePrisonerContactRequest): MergePrisonerContactResponse = webClient.post()
    .uri("/sync/prisoner/{prisonerNumber}/contact/replace", prisonerNumber)
    .bodyValue(mergePrisonerContactRequest)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}

// FAKE DTOs - replace with real ones when delivered by DPS

data class SyncPrisonerRelationship(
  val id: Long,
  val contactType: CodedValue,
  val relationshipType: CodedValue,
  val currentTerm: Boolean,
  val active: Boolean,
  val approvedVisitor: Boolean,
  val nextOfKin: Boolean,
  val emergencyContact: Boolean,
  val contactId: Long,
  val restrictions: List<MigratePrisonerContactRestriction>,
  val expiryDate: java.time.LocalDate? = null,
  val comment: String? = null,
  val createDateTime: java.time.LocalDateTime? = null,
  val createUsername: String? = null,
  val modifyDateTime: java.time.LocalDateTime? = null,
  val modifyUsername: String? = null,
)

data class MergePrisonerContactRequest(
  val prisonerContacts: List<SyncPrisonerRelationship>,
  val removedPrisonerNumber: String,
)

data class MergePrisonerContactResponse(
  val prisonerContacts: List<ContactsAndRestrictions>,
  val relationshipsRemoved: List<Long>,
  val restrictionsRemoved: List<Long>,
)
