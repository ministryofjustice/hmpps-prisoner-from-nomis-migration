package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.identifyingmarks

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.BookingIdentifyingMarksResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IdentifyingMarkImageDetailsResponse

@Service
class IdentifyingMarksNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getIdentifyingMarks(bookingId: Long): BookingIdentifyingMarksResponse = webClient.get()
    .uri("/bookings/{bookingId}/identifying-marks", bookingId)
    .retrieve()
    .awaitBody()

  suspend fun getIdentifyingMarksImageDetails(offenderImageId: Long): IdentifyingMarkImageDetailsResponse = webClient.get()
    .uri("/identifying-marks/images/{offenderImageId}/details", offenderImageId)
    .retrieve()
    .awaitBody()

  suspend fun getIdentifyingMarksImageData(offenderImageId: Long): ByteArray = webClient.get()
    .uri("/identifying-marks/images/{offenderImageId}/data", offenderImageId)
    .retrieve()
    .awaitBody()
}
