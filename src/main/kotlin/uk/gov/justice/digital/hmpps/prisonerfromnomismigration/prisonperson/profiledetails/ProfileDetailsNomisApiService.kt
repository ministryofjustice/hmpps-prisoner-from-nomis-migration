package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerProfileDetailsResponse

@Service
class ProfileDetailsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getProfileDetails(offenderNo: String): PrisonerProfileDetailsResponse = webClient.get()
    .uri("/prisoners/{offenderNo}/profile-details", offenderNo)
    .retrieve()
    .awaitBody()
}
