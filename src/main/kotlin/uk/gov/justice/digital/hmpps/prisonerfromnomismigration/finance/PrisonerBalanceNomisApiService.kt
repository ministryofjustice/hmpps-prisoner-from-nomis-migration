package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.PrisonerBalanceResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PagedModelLong
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.RootOffenderIdRange

@Service
class PrisonerBalanceNomisApiService(@Qualifier("nomisApiWebClient") webClient: WebClient) {
  private val api = PrisonerBalanceResourceApi(webClient)

  suspend fun getRootOffenderIdsToMigrate(prisonId: String?, pageNumber: Long, pageSize: Long): PagedModelLong? = api
    .getPrisonerBalanceIdentifiers(page = pageNumber.toInt(), size = pageSize.toInt(), sort = null, prisonId = if (prisonId != null) listOf(prisonId) else null)
    .awaitSingle()

  suspend fun getPrisonerBalanceIdentifiersInRange(fromRootOffenderId: Long, toRootOffenderId: Long, prisonId: String?): List<Long> = api
    .getPrisonerBalanceIdentifiersInRange(fromRootOffenderId = fromRootOffenderId, toRootOffenderId = toRootOffenderId, prisonId = if (prisonId != null) listOf(prisonId) else null)
    .awaitSingle()

  suspend fun getPrisonerBalanceForMigration(rootOffenderId: Long): PrisonerBalanceDto = api
    .getPrisonerAccountDetailsForMigration(rootOffenderId)
    .awaitSingle()

  suspend fun getAllPrisonersIdRanges(pageSize: Long, prisonId: String?): List<RootOffenderIdRange> = api
    .getPrisonerBalanceIdentifierRanges(pageSize.toInt(), prisonId = if (prisonId != null) listOf(prisonId) else null)
    .awaitSingle()
}
