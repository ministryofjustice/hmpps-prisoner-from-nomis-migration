package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.api.SyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.ReferenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.transferschedule.model.SyncTransferRequest

@Service
class TransferScheduleDpsApiService(@Qualifier("transferScheduleDpsApiWebClient") private val webClient: WebClient) {

  private val syncApi = SyncApi(webClient)

  suspend fun syncTransferSchedule(personIdentifier: String, request: SyncTransferRequest): ReferenceId = syncApi.prepare(syncApi.syncTransferRequestConfig(personIdentifier, request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
