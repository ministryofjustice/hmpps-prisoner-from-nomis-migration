package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerProfileDetailsResponse

@Service
class ContactPersonProfileDetailsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getProfileDetails(offenderNo: String, profileTypes: List<String> = emptyList(), bookingId: Long? = null): PrisonerProfileDetailsResponse = webClient.get()
    .uri {
      it.path("/prisoners/{offenderNo}/profile-details")
        .queryParam("profileTypes", profileTypes)
        .apply { bookingId?.run { queryParam("bookingId", "$bookingId") } }
        .build(offenderNo)
    }
    .retrieve()
    .awaitBody()
}
