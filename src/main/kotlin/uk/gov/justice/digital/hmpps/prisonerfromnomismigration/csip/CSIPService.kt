package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound

@Service
class CSIPService(@Qualifier("csipApiWebClient") private val webClient: WebClient) {
  suspend fun migrateCSIP(migrateRequest: CSIPMigrateRequest): CSIPMigrateResponse =
    webClient.post()
      .uri("/migrate/csip-report")
      .bodyValue(migrateRequest)
      .retrieve()
      .awaitBody()

  suspend fun createCSIP(csip: CSIPSyncRequest): CSIPSyncResponse =
    webClient.post()
      .uri("/csip")
      .bodyValue(csip)
      .retrieve()
      .awaitBody()

  suspend fun deleteCSIP(dpsCSIPId: String) =
    webClient.delete()
      .uri("/csip/{dpsCSIPId}", dpsCSIPId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound<Unit>()
}
