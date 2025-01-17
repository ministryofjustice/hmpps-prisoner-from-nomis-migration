package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorePerson

@Service
class CorePersonNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  suspend fun getCorePerson(nomisPrisonNumber: String): CorePerson = webClient.get()
    .uri("/core-person/{nomisPrisonNumber}", nomisPrisonNumber)
    .retrieve()
    .awaitBody()
}
