package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactPerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerWithContacts
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerWithRestrictions

@Service
class ContactPersonNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getPerson(nomisPersonId: Long): ContactPerson = webClient.get()
    .uri(
      "/persons/{personId}",
      nomisPersonId,
    )
    .retrieve()
    .awaitBody()

  suspend fun getContact(nomisContactId: Long): PersonContact = webClient.get()
    .uri(
      "/contact/{contactId}",
      nomisContactId,
    )
    .retrieve()
    .awaitBody()

  suspend fun getContactsForPrisoner(
    offenderNo: String,
  ): PrisonerWithContacts = webClient.get()
    .uri("/prisoners/{offenderNo}/contacts?active-only=false&latest-booking-only=false", offenderNo)
    .retrieve()
    .awaitBody()

  suspend fun getPrisonerDetails(offenderNo: String): PrisonerDetails = webClient.get()
    .uri(
      "/prisoners/{offenderNo}",
      offenderNo,
    )
    .retrieve()
    .awaitBody()

  suspend fun getPrisonerRestrictionById(
    prisonerRestrictionId: Long,
  ): PrisonerRestriction = webClient.get()
    .uri("/prisoners/restrictions/{restrictionId}", prisonerRestrictionId)
    .retrieve()
    .awaitBody()

  suspend fun getPrisonerRestrictions(offenderNo: String): PrisonerWithRestrictions = webClient.get()
    .uri("/prisoners/{offenderNo}/restrictions?latest-booking-only=false", offenderNo)
    .retrieve()
    .awaitBody()
}
