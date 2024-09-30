package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncCsipRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest

@Service
class CSIPDpsApiService(@Qualifier("csipApiWebClient") private val webClient: WebClient) {

  suspend fun migrateCSIP(syncRequest: SyncCsipRequest): SyncResponse =
    webClient.put()
      .uri("/sync/csip-records")
      .bodyValue(syncRequest)
      .retrieve()
      .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncCSIP(syncRequest: SyncCsipRequest): SyncResponse =
    webClient.put()
      .uri("/sync/csip-records")
      .bodyValue(syncRequest)
      .retrieve()
      .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteCSIP(csipReportId: String) {
    webClient.delete()
      .uri("/sync/csip-records/{csipReportId}", csipReportId)
      .retrieve()
      .awaitBodilessEntity()
  }
}
