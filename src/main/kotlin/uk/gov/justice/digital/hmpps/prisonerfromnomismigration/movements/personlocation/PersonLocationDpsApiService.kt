package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.api.SyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ResyncExternalMovementsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ResyncResponse

@Service
class PersonLocationDpsApiService(@Qualifier("personLocationDpsApiWebClient") private val webClient: WebClient) {

  private val syncApi = SyncApi(webClient)

  suspend fun resyncPerson(personIdentifier: String, request: ResyncExternalMovementsRequest) = syncApi.prepare(syncApi.resyncRequestConfig(personIdentifier, request))
    .retrieve()
    .awaitBodyOrNullWhenNotFound<ResyncResponse>()
}
