package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactPerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonContact
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerRestriction
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerRestrictionIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerWithContacts
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerWithRestrictions
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.typeReference
import java.time.LocalDate

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

  suspend fun getPrisonerRestrictionIdsToMigrate(
    fromDate: LocalDate? = null,
    toDate: LocalDate? = null,
    pageNumber: Long = 0,
    pageSize: Long = 1,
  ): PageImpl<PrisonerRestrictionIdResponse> = webClient.get()
    .uri {
      it.path("/prisoners/restrictions/ids")
        .queryParam("fromDate", fromDate)
        .queryParam("toDate", toDate)
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .bodyToMono(typeReference<RestResponsePage<PrisonerRestrictionIdResponse>>())
    .awaitSingle()

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
