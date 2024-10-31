package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.Alert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.CreateAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.ResyncAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.ResyncedAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.UpdateAlert

@Service
class AlertsDpsApiService(@Qualifier("alertsApiWebClient") private val webClient: WebClient) {
  suspend fun createAlert(offenderNo: String, alert: CreateAlert, createdByUsername: String): Alert =
    webClient
      .post()
      .uri("/prisoners/{prisonNumber}/alerts", offenderNo)
      .bodyValue(alert)
      .header("Source", "NOMIS")
      .header("Username", createdByUsername)
      .retrieve()
      .awaitBody()

  suspend fun updateAlert(alertId: String, alert: UpdateAlert, updatedByUsername: String): Alert =
    webClient
      .put()
      .uri("/alerts/{alertId}", alertId)
      .bodyValue(alert)
      .header("Source", "NOMIS")
      .header("Username", updatedByUsername)
      .retrieve()
      .awaitBody()

  suspend fun deleteAlert(alertId: String) {
    webClient
      .delete()
      .uri("/alerts/{alertId}", alertId)
      .header("Source", "NOMIS")
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun resynchroniseAlerts(offenderNo: String, alerts: List<ResyncAlert>): List<ResyncedAlert> = webClient
    .post()
    .uri("/resync/{offenderNo}/alerts", offenderNo)
    .bodyValue(alerts)
    .retrieve()
    .awaitBody()
}
