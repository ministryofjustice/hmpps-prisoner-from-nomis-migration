package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.NomisAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.NomisAlertMapping

@Service
class AlertsDpsApiService(@Qualifier("alertsApiWebClient") private val webClient: WebClient) {
  suspend fun createAlert(alert: NomisAlert): NomisAlertMapping =
    webClient
      .post()
      .uri("/alerts")
      .bodyValue(alert)
      .retrieve()
      .awaitBody()

  suspend fun updateAlert(alert: NomisAlert): NomisAlertMapping =
    // for now DPS provide the same endpoint for update and insert
    // I wouldn't be surprised of this changes given it ties DPS to NOMIS
    webClient
      .post()
      .uri("/alerts")
      .bodyValue(alert)
      .retrieve()
      .awaitBody()
}
