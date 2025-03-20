package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerVisitBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RestResponsePage

@Service
class VisitBalanceNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getVisitBalance(visitBalanceId: Long): PrisonerVisitBalanceResponse = webClient.get()
    .uri("/visit-balances/{visitBalanceId}", visitBalanceId)
    .retrieve()
    .awaitBody()

  suspend fun getVisitBalanceAdjustment(visitBalanceAdjustmentId: Long): VisitBalanceAdjustmentResponse = webClient.get()
    .uri("/visit-balances/visit-balance-adjustment/{visitBalanceAdjustmentId}", visitBalanceAdjustmentId)
    .retrieve()
    .awaitBody()

  suspend fun getVisitBalanceIds(prisonId: String?, pageNumber: Long, pageSize: Long): RestResponsePage<VisitBalanceIdResponse> = webClient.get()
    .uri {
      it.path("/visit-balances/ids")
        .queryParam("prisonId", prisonId)
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .awaitBody()
}
