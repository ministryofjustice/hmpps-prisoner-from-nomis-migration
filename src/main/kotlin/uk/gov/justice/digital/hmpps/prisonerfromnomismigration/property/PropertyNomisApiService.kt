package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.PropertyResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PageContainerIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PropertyContainerGetResponse

@Service
class PropertyNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  private val api = PropertyResourceApi(webClient)

  suspend fun getPropertyContainerIds(
    pageNumber: Long = 0,
    pageSize: Long = 1,
    prisonIds: List<String>,
  ): PageContainerIdResponse = api.getPropertyContainersByFilter(
    page = pageNumber.toInt(),
    size = pageSize.toInt(),
    prisonIds = prisonIds,
  ).awaitSingle()

  suspend fun getPropertyContainer(id: Long): PropertyContainerGetResponse = api.get(id).awaitSingle()
}
