package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.api.SyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.ReferenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.ResyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.ResyncTransfersRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncMovementRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncTransferRequest
import java.util.UUID

@Service
class TransferScheduleDpsApiService(@Qualifier("transferScheduleDpsApiWebClient") private val webClient: WebClient) {

  private val syncApi = SyncApi(webClient)

  suspend fun syncTransferSchedule(personIdentifier: String, request: SyncTransferRequest): ReferenceId = syncApi.prepare(syncApi.syncTransferRequestConfig(personIdentifier, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteTransferSchedule(dpsId: UUID) = syncApi.deleteTransfer(dpsId).awaitSingle()

  suspend fun syncTransferMovement(personIdentifier: String, request: SyncMovementRequest): ReferenceId = syncApi.prepare(syncApi.syncMovementRequestConfig(personIdentifier, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteTransferMovement(dpsId: UUID) = syncApi.deleteMovement(dpsId).awaitSingle()

  suspend fun resyncPrisoner(personIdentifier: String, request: ResyncTransfersRequest) = syncApi.prepare(syncApi.resyncRequestConfig(personIdentifier, request))
    .retrieve()
    .awaitBodyOrNullWhenNotFound<ResyncResponse>()
}
