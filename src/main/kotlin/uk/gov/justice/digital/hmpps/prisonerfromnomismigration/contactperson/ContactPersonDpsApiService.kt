package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateContactResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncContactAddress
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactAddressRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncCreatePrisonerContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.SyncPrisonerContact
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
}
