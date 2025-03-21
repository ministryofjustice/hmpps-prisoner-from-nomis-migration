package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactPerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerWithContacts
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

  suspend fun getPersonIdsToMigrate(
    fromDate: LocalDate? = null,
    toDate: LocalDate? = null,
    pageNumber: Long = 0,
    pageSize: Long = 1,
  ): PageImpl<PersonIdResponse> = webClient.get()
    .uri {
      it.path("/persons/ids")
        .queryParam("fromDate", fromDate)
        .queryParam("toDate", toDate)
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .bodyToMono(typeReference<RestResponsePage<PersonIdResponse>>())
    .awaitSingle()

  suspend fun getContactsForPrisoner(
    offenderNo: String,
  ): PrisonerWithContacts = webClient.get()
    .uri("/prisoners/{offenderNo}/contacts", offenderNo)
    .retrieve()
    .awaitBody()

  suspend fun getPrisonerDetails(offenderNo: String): PrisonerDetails = webClient.get()
    .uri(
      "/prisoners/{offenderNo}",
      offenderNo,
    )
    .retrieve()
    .awaitBody()
}
