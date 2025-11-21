package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.OfficialVisitsResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OfficialVisitResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PagedModelVisitIdResponse

@Service
class OfficialVisitsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val api = OfficialVisitsResourceApi(webClient)

  suspend fun getOfficialVisitIds(
    pageNumber: Long = 0,
    pageSize: Long = 1,
  ): PagedModelVisitIdResponse = api.getOfficialVisitIds(
    page = pageNumber.toInt(),
    size = pageSize.toInt(),
  ).awaitSingle()

  suspend fun getOfficialVisit(
    visitId: Long,
  ): OfficialVisitResponse = api.getOfficialVisit(
    visitId = visitId,
  ).awaitSingle()
}
