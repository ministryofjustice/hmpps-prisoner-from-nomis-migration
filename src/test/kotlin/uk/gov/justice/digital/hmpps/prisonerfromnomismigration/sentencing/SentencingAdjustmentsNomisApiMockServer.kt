package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.KeyDateAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SentenceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SentencingAdjustmentsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi

@Component
class SentencingAdjustmentsNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetAllByBookingId(
    bookingId: Long = 123456,
    sentenceAdjustments: List<SentenceAdjustmentResponse> = emptyList(),
    keyDateAdjustments: List<KeyDateAdjustmentResponse> = emptyList(),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/booking-id/$bookingId/sentencing-adjustments?active-only=false")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(SentencingAdjustmentsResponse(keyDateAdjustments, sentenceAdjustments))),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
}
