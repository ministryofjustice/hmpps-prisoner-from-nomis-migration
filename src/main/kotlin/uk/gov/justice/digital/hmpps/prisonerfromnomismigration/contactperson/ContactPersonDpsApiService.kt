package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.Contact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.CreateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateContactRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson.model.MigrateContactResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest

@Service
class ContactPersonDpsApiService(@Qualifier("contactPersonApiWebClient") private val webClient: WebClient) {
  suspend fun migrateContact(contact: MigrateContactRequest): MigrateContactResponse = webClient.post()
    .uri("/migrate/contact")
    .bodyValue(contact)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun createContact(contact: CreateContactRequest): Contact = webClient.post()
    .uri("/sync/contact")
    .bodyValue(contact)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
