package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.Alert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.CreateAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.MigrateAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.MigrateAlertRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.UpdateAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenConflict

@Service
class AlertsDpsApiService(@Qualifier("alertsApiWebClient") private val webClient: WebClient) {
  suspend fun createAlert(alert: CreateAlert, createdByUsername: String): Alert =
    webClient
      .post()
      .uri("/alerts")
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

  suspend fun migrateAlert(alert: MigrateAlertRequest): Alert? =
    webClient
      .post()
      .uri("/migrate/alerts")
      .bodyValue(alert)
      .retrieve()
      .awaitBodyOrNullWhenConflict()

  suspend fun migrateAlerts(offenderNo: String, alerts: List<MigrateAlert>): List<Alert> = webClient
    .post()
    .uri("/migrate/{offenderNo}/alerts", offenderNo)
    .bodyValue(alerts)
    .retrieve()
    .awaitBody()
}

data class AlertsForPrisonerResponse(
  val alerts: List<Alert>,
)
