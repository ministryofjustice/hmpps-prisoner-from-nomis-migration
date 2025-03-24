package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CSIPFactorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CSIPIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CSIPResponse

@Service
class CSIPNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  suspend fun getCSIP(csipId: Long): CSIPResponse = webClient.get()
    .uri("/csip/{csipId}", csipId)
    .retrieve()
    .awaitBody()

  suspend fun getCSIPFactor(csipFactorId: Long): CSIPFactorResponse = webClient.get()
    .uri("/csip/factors/{csipFactorId}", csipFactorId)
    .retrieve()
    .awaitBody()

  suspend fun getCSIPsForBooking(bookingId: Long): List<CSIPIdResponse> = webClient.get()
    .uri("/csip/booking/{bookingId}", bookingId)
    .retrieve()
    .awaitBody()
}
