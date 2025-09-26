package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.PrisonerBalanceResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RestResponsePagedModel
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.typeReference

@Service
class PrisonerBalanceNomisApiService(@Qualifier("nomisApiWebClient") webClient: WebClient) {
  private val api = PrisonerBalanceResourceApi(webClient)

  suspend fun getRootOffenderIdsToMigrate(prisonId: String?, pageNumber: Long, pageSize: Long): RestResponsePagedModel<Long> = api
    .prepare(api.getPrisonerBalanceIdentifiersRequestConfig(page = pageNumber.toInt(), size = pageSize.toInt(), sort = null, prisonId = prisonId))
    .retrieve()
    .bodyToMono(typeReference<RestResponsePagedModel<Long>>())
    .awaitSingle()

  suspend fun getPrisonerBalance(rootOffenderId: Long): PrisonerBalanceDto = api
    .getPrisonerAccountDetails(rootOffenderId)
    .awaitSingle()
}
