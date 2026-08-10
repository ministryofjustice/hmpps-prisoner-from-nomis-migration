package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.PrisonerBalanceResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PagedModelLong
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.RootOffenderIdsWithLast

@Service
class PrisonerBalanceNomisApiService(@Qualifier("nomisApiWebClient") webClient: WebClient) {
  private val api = PrisonerBalanceResourceApi(webClient)

  suspend fun getRootOffenderIdsToMigrate(prisonId: String?, pageNumber: Long, pageSize: Long): PagedModelLong? = api
    .getPrisonerBalanceIdentifiers(page = pageNumber.toInt(), size = pageSize.toInt(), sort = null, prisonId = prisonId.takeUnless { it.isNullOrBlank() }?.let(::listOf))
    .awaitSingle()

  suspend fun getPrisonerBalanceIdentifiersFromId(rootOffender: Long, prisonId: String?, pageSize: Long): RootOffenderIdsWithLast? = api
    .getPrisonerBalanceIdentifiersFromId(rootOffenderId = rootOffender, pageSize = pageSize.toInt(), prisonId = prisonId.takeUnless { it.isNullOrBlank() }?.let(::listOf))
    .awaitSingle()

  suspend fun getPrisonerBalanceForMigration(rootOffenderId: Long): PrisonerBalanceDto = api
    .getPrisonerAccountDetailsForMigration(rootOffenderId)
    .awaitSingle()
}
