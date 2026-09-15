package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.DrugTestingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.IdRange
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.RandomTestingProgramResponse

@Service
class DrugTestingNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val api = DrugTestingResourceApi(webClient)

  suspend fun getRandomTestingProgram(rtpId: Long): RandomTestingProgramResponse = api
    .getRandomTestingProgramWithOffenders(rtpId = rtpId)
    .awaitSingle()

  suspend fun getDrugTestingIdRanges(pageSize: Int? = 1000, filter: DrugTestingMigrationFilter) = api.getDrugTestingIdRanges(pageSize = pageSize, includedPrisonIds = filter.includedPrisonIds, excludedPrisonIds = filter.excludedPrisonIds)
    .awaitSingle()

  suspend fun getDrugTestingIdsInRange(idRange: IdRange, filter: DrugTestingMigrationFilter) = api.getDrugTestingIdsInRange(fromId = idRange.fromId, toId = idRange.toId, includedPrisonIds = filter.includedPrisonIds, excludedPrisonIds = filter.excludedPrisonIds)
    .awaitSingle()
}
