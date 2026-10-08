package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitSingleOrNullForNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.PrisonerAdvanceResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AdvancesCount
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerAdvanceDto

@Service
class AdvancesNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val api = PrisonerAdvanceResourceApi(webClient)

  suspend fun getAdvance(advanceId: Long): PrisonerAdvanceDto? = api
    .getAdvance(advanceId)
    .awaitSingleOrNullForNotFound()

  suspend fun getPrisonerAdvances(rootOffenderId: Long): List<PrisonerAdvanceDto> = api
    .getPrisonerAdvancesById(rootOffenderId)
    .awaitSingle()

  suspend fun getActivePrisonerAdvances(rootOffenderId: Long): List<PrisonerAdvanceDto> = api
    .getPrisonerAdvancesById(rootOffenderId = rootOffenderId, activeOnly = true)
    .awaitSingle()

  suspend fun getActiveAdvancesCount(): AdvancesCount = api
    .getActiveAdvancesCount()
    .awaitSingle()
}
