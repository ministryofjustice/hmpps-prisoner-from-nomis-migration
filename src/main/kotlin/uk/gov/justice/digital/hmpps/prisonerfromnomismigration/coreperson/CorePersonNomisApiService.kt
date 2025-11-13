package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.util.TypeUtils.type
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ProfileDetailsResponse

@Service
class CorePersonNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  suspend fun getCorePerson(nomisPrisonNumber: String): CorePerson = webClient.get()
    .uri("/core-person/{nomisPrisonNumber}", nomisPrisonNumber)
    .retrieve()
    .awaitBody()

  suspend fun getProfileDetail(bookingId: Long, sequence: Int, typeString: String): ProfileDetailsResponse = webClient.get()
    .uri("/profile-details/{bookingId}/sequence/{sequence}/type/{type}", bookingId, sequence, typeString)
    .retrieve()
    .awaitBody()
}
