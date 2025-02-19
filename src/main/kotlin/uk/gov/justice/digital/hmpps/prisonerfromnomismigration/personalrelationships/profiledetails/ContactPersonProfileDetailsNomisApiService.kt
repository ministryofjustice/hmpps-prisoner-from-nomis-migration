package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerProfileDetailsResponse

@Service
class ContactPersonProfileDetailsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getProfileDetails(offenderNo: String): PrisonerProfileDetailsResponse = webClient.get()
    .uri("/prisoners/{offenderNo}/profile-details", offenderNo)
    .retrieve()
    .awaitBody()
}
