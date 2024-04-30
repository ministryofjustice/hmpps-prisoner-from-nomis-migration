package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Service
class CSIPService(@Qualifier("csipApiWebClient") private val webClient: WebClient) {
  suspend fun migrateCSIP(migrateRequest: CSIPMigrateRequest): CSIPMigrateResponse =
    webClient.post()
      .uri("/csip/migrate")
      .bodyValue(migrateRequest)
      .retrieve()
      .awaitBody()
}
