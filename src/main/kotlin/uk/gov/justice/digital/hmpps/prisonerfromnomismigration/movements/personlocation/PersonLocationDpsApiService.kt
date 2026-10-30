package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.api.SyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ReferenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ResyncExternalMovementsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.ResyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncCustodialSeriesRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personlocation.model.SyncExternalMovementRequest
import java.util.UUID

@Service
class PersonLocationDpsApiService(@Qualifier("personLocationDpsApiWebClient") private val webClient: WebClient) {

  private val syncApi = SyncApi(webClient)

  suspend fun resyncPerson(personIdentifier: String, request: ResyncExternalMovementsRequest) = syncApi.prepare(syncApi.resyncRequestConfig(personIdentifier, request))
    .retrieve()
    .awaitBodyOrNullWhenNotFound<ResyncResponse>()

  suspend fun syncExternalMovement(personIdentifier: String, request: SyncExternalMovementRequest): ReferenceId = syncApi.prepare(syncApi.syncExternalMovementRequestConfig(personIdentifier, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteExternalMovement(dpsId: UUID) = syncApi.deleteExternalMovement(dpsId).awaitSingle()

  suspend fun deleteCustodialSeries(dpsId: UUID) = syncApi.deleteCustodialSeries(dpsId).awaitSingle()

  suspend fun syncCustodialSeries(personIdentifier: String, request: SyncCustodialSeriesRequest): ReferenceId = syncApi.prepare(syncApi.syncCustodialSeriesRequestConfig(personIdentifier, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
