package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RestResponsePage
import java.time.LocalDate

@Service
class AlertsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getAlert(bookingId: Long, alertSequence: Long): AlertResponse = webClient.get()
    .uri(
      "/prisoner/booking-id/{bookingId}/alerts/{alertSequence}",
      bookingId,
      alertSequence,
    )
    .retrieve()
    .awaitBody()

  suspend fun getAlertIds(fromDate: LocalDate?, toDate: LocalDate?, pageNumber: Long, pageSize: Long): RestResponsePage<AlertIdResponse> = webClient.get()
    .uri {
      it.path("/alerts/ids")
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .queryParam("fromDate", fromDate)
        .queryParam("toDate", toDate)
        .build()
    }
    .retrieve()
    .awaitBody()
}
