package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import kotlinx.coroutines.reactive.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageImpl
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPFactorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RestResponsePage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.typeReference
import java.time.LocalDate

@Service
class CSIPNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {

  suspend fun getCSIP(csipId: Long): CSIPResponse =
    webClient.get()
      .uri("/csip/{csipId}", csipId)
      .retrieve()
      .awaitBody()

  suspend fun getCSIPFactor(csipFactorId: Long): CSIPFactorResponse =
    webClient.get()
      .uri("/csip/factors/{csipFactorId}", csipFactorId)
      .retrieve()
      .awaitBody()

  suspend fun getCSIPsForBooking(bookingId: Long): List<CSIPIdResponse> =
    webClient.get()
      .uri("/csip/booking/{bookingId}", bookingId)
      .retrieve()
      .awaitBody()

  suspend fun getCSIPIds(
    fromDate: LocalDate?,
    toDate: LocalDate?,
    pageNumber: Long,
    pageSize: Long,
  ): PageImpl<CSIPIdResponse> =
    webClient.get()
      .uri {
        it.path("/csip/ids")
          .queryParam("fromDate", fromDate)
          .queryParam("toDate", toDate)
          .queryParam("page", pageNumber)
          .queryParam("size", pageSize)
          .build()
      }
      .retrieve()
      .bodyToMono(typeReference<RestResponsePage<CSIPIdResponse>>())
      .awaitSingle()
}
