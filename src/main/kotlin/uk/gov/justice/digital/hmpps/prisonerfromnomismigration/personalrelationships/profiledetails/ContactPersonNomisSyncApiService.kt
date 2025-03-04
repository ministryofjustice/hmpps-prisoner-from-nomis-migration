package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity

@Service
class ContactPersonNomisSyncApiService(@Qualifier("nomisSyncApiWebClient") private val webClient: WebClient) {
  suspend fun syncProfileDetails(prisonerNumber: String, profileType: String) = webClient.put()
    .uri("/contactperson/sync/profile-details/{prisonerNumber}/{profileType}", prisonerNumber, profileType)
    .retrieve()
    .awaitBodilessEntity()
}
