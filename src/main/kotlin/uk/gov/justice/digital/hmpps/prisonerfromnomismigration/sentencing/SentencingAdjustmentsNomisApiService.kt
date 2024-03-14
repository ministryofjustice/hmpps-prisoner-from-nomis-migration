package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SentencingAdjustmentsResponse

@Service
class SentencingAdjustmentsNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getAllByBookingId(bookingId: Long): SentencingAdjustmentsResponse = webClient.get()
    .uri(
      "/prisoners/booking-id/{bookingId}/sentencing-adjustments?active-only=false",
      bookingId,
    )
    .retrieve()
    .awaitBody()
}
