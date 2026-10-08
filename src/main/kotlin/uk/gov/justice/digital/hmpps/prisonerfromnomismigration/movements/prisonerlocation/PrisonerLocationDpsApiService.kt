package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.prisonerlocation

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonerlocation.api.SyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonerlocation.model.ResyncExternalMovementsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonerlocation.model.ResyncResponse

@Service
class PrisonerLocationDpsApiService(@Qualifier("prisonerLocationDpsApiWebClient") private val webClient: WebClient) {

  private val syncApi = SyncApi(webClient)

  suspend fun resyncPrisoner(personIdentifier: String, request: ResyncExternalMovementsRequest) = syncApi.prepare(syncApi.resyncRequestConfig(personIdentifier, request))
    .retrieve()
    .awaitBodyOrNullWhenNotFound<ResyncResponse>()
}
