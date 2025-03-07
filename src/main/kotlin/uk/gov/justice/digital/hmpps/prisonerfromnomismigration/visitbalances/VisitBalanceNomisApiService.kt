package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerVisitOrderBalanceResponse

@Service
class VisitBalanceNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getVisitBalance(prisonNumber: String): PrisonerVisitOrderBalanceResponse = webClient.get()
    .uri("/prisoners/{prisonNumber}/visit-orders/balance", prisonNumber)
    .retrieve()
    .awaitBody()
}
