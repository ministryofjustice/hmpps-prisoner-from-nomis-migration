package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerAlertsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerDetails

@Service
class AlertsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getAlert(bookingId: Long, alertSequence: Long): AlertResponse = webClient.get()
    .uri(
      "/prisoners/booking-id/{bookingId}/alerts/{alertSequence}",
      bookingId,
      alertSequence,
    )
    .retrieve()
    .awaitBody()

  suspend fun getAlertsToResynchronise(offenderNo: String): PrisonerAlertsResponse? = webClient.get()
    .uri(
      "/prisoners/{offenderNo}/alerts/to-migrate",
      offenderNo,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getPrisonerDetails(offenderNo: String): PrisonerDetails = webClient.get()
    .uri(
      "/prisoners/{offenderNo}",
      offenderNo,
    )
    .retrieve()
    .awaitBody()
}
