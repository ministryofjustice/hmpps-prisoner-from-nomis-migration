package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.Alert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.CreateAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.MergeAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.MergeAlerts
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.MergedAlerts
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.MigrateAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.MigratedAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.ResyncAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.ResyncedAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.UpdateAlert
import java.util.*

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

  suspend fun migrateAlerts(offenderNo: String, alerts: List<MigrateAlert>): List<MigratedAlert> = webClient
    .post()
    .uri("/migrate/{offenderNo}/alerts", offenderNo)
    .bodyValue(alerts)
    .retrieve()
    .awaitBody()

  suspend fun resynchroniseAlerts(offenderNo: String, alerts: List<ResyncAlert>): List<ResyncedAlert> = webClient
    .post()
    .uri("/resync/{offenderNo}/alerts", offenderNo)
    .bodyValue(alerts)
    .retrieve()
    .awaitBody()

  suspend fun mergePrisonerAlerts(offenderNo: String, removedOffenderNo: String, alerts: List<MergeAlert>, retainedAlertIds: List<String>): MergedAlerts = webClient
    .post()
    .uri("/merge-alerts")
    .bodyValue(
      MergeAlerts(
        prisonNumberMergeTo = offenderNo,
        prisonNumberMergeFrom = removedOffenderNo,
        newAlerts = alerts,
        retainedAlertUuids = retainedAlertIds.map { UUID.fromString(it) },
      ),
    )
    .retrieve()
    .awaitBody()
}

data class AlertsForPrisonerResponse(
  val alerts: List<Alert>,
)
