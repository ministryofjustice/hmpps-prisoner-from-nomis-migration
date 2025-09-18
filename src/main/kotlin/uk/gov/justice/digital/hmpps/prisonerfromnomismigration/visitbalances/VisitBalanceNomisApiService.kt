package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.VisitBalanceResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Pageable
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceDetailResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RestResponsePage

@Service
class VisitBalanceNomisApiService(@Qualifier("nomisApiWebClient") webClient: WebClient) {
  private val api = VisitBalanceResourceApi(webClient)
  suspend fun getVisitBalanceDetail(visitBalanceId: Long): VisitBalanceDetailResponse = api
    .prepare(api.getVisitBalanceByIdToMigrateRequestConfig(visitBalanceId))
    .retrieve()
    .awaitBody()

  suspend fun getVisitBalanceDetailForPrisoner(prisonNumber: String): VisitBalanceDetailResponse? = api
    .prepare(api.getVisitBalanceDetailsForPrisonerRequestConfig(prisonNumber))
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getVisitBalanceAdjustment(visitBalanceAdjustmentId: Long): VisitBalanceAdjustmentResponse = api
    .prepare(api.getVisitBalanceAdjustmentRequestConfig(visitBalanceAdjustmentId))
    .retrieve()
    .awaitBody()

  suspend fun getVisitBalanceIds(prisonId: String?, pageNumber: Long, pageSize: Long): RestResponsePage<VisitBalanceIdResponse> = api
    .prepare(api.findVisitBalanceIdsRequestConfig(Pageable(pageNumber.toInt(), pageSize.toInt()), prisonId))
    .retrieve()
    .awaitBody()
}
