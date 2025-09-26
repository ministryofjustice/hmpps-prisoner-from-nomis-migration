package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.PrisonerBalanceResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Pageable
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RestResponsePagedModel

@Service
class PrisonerBalanceNomisApiService(@Qualifier("nomisApiWebClient") webClient: WebClient) {
  private val api = PrisonerBalanceResourceApi(webClient)

  suspend fun getRootOffenderIdsToMigrate(prisonId: String?, pageNumber: Long, pageSize: Long): RestResponsePagedModel<Long> = api
    .prepare(api.getPrisonerIdentifiers1RequestConfig(Pageable(pageNumber.toInt(), pageSize.toInt()), prisonId))
    .retrieve()
    .awaitBody()

  suspend fun getPrisonerBalance(rootOffenderId: Long): PrisonerBalanceDto = api
    .prepare(api.getPrisonerAccountDetailsRequestConfig(rootOffenderId))
    .retrieve()
    .awaitBody()
}
