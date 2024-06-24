package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.BookingAlertsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PreviousBookingId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerAlertsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RestResponsePage

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

  suspend fun getPrisonerIds(pageNumber: Long, pageSize: Long): RestResponsePage<PrisonerId> = webClient.get()
    .uri {
      it.path("/prisoners/ids/all")
        .queryParam("page", pageNumber)
        .queryParam("size", pageSize)
        .build()
    }
    .retrieve()
    .awaitBody()

  suspend fun getAlertsToMigrate(offenderNo: String): PrisonerAlertsResponse? = webClient.get()
    .uri(
      "/prisoners/{offenderNo}/alerts/to-migrate",
      offenderNo,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getAlertsToResynchronise(offenderNo: String): PrisonerAlertsResponse? = webClient.get()
    .uri(
      "/prisoners/{offenderNo}/alerts/to-migrate",
      offenderNo,
    )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun getAlertsByBookingId(bookingId: Long): BookingAlertsResponse = webClient.get()
    .uri(
      "/prisoners/booking-id/{bookingId}/alerts",
      bookingId,
    )
    .retrieve()
    .awaitBody()

  suspend fun getBookingPreviousTo(offenderNo: String, bookingId: Long): PreviousBookingId = webClient.get()
    .uri(
      "/prisoners/{offenderNo}/bookings/{bookingId}/previous",
      offenderNo,
      bookingId,
    )
    .retrieve()
    .awaitBody()
}
