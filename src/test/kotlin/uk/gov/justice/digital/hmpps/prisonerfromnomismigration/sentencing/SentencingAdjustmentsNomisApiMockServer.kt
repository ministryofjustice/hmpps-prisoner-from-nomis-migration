package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.KeyDateAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentencingAdjustmentsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
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

  fun stubGetSentenceAdjustment(
    adjustmentId: Long,
    hiddenForUsers: Boolean = false,
    prisonId: String = "MDI",
    bookingId: Long = 2,
    sentenceSequence: Long = 1,
    offenderNo: String = "G4803UT",
    bookingSequence: Int = 1,
  ) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/sentence-adjustments/$adjustmentId"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              sentenceAdjustmentResponse(
                sentenceAdjustmentId = adjustmentId,
                hiddenForUsers = hiddenForUsers,
                prisonId = prisonId,
                bookingId = bookingId,
                sentenceSequence = sentenceSequence,
                offenderNo = offenderNo,
                bookingSequence = bookingSequence,
              ),
            ),
        ),
    )
  }

  fun stubGetSentenceAdjustment(
    adjustmentId: Long,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/sentence-adjustments/$adjustmentId"),
      )
        .willReturn(
          aResponse()
            .withStatus(status.value())
            .withHeader("Content-Type", "application/json")
            .withBody(NomisApiExtension.jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubGetKeyDateAdjustment(
    adjustmentId: Long,
    prisonId: String = "MDI",
    bookingId: Long = 2,
    offenderNo: String = "G4803UT",
    bookingSequence: Int = 1,
  ) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/key-date-adjustments/$adjustmentId"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              keyDateAdjustmentResponse(
                keyDateAdjustmentId = adjustmentId,
                prisonId = prisonId,
                bookingId = bookingId,
                offenderNo = offenderNo,
                bookingSequence = bookingSequence,
              ),
            ),
        ),
    )
  }

  fun stubGetKeyDateAdjustment(
    adjustmentId: Long,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/key-date-adjustments/$adjustmentId"),
      )
        .willReturn(
          aResponse()
            .withStatus(status.value())
            .withHeader("Content-Type", "application/json")
            .withBody(NomisApiExtension.jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun verify(countMatchingStrategy: CountMatchingStrategy, pattern: RequestPatternBuilder) = nomisApi.verify(countMatchingStrategy, pattern)
  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
}
private fun keyDateAdjustmentResponse(
  bookingId: Long = 2,
  keyDateAdjustmentId: Long = 3,
  offenderNo: String = "G4803UT",
  prisonId: String = "MDI",
  bookingSequence: Int = 1,
): String {
  // language=JSON
  return """
{
  "bookingId":$bookingId,
  "bookingSequence":$bookingSequence,
  "id":$keyDateAdjustmentId,
  "offenderNo": "$offenderNo",
  "commentText":"a comment",
  "adjustmentDate":"2021-10-06",
  "adjustmentFromDate":"2021-10-07",
  "active":true,
  "adjustmentDays":8,
  "adjustmentType": {
    "code": "ADA",
    "description": "Additional days"
  },
  "hasBeenReleased": false,
  "prisonId": "$prisonId"
}
   
  """.trimIndent()
}

private fun sentenceAdjustmentResponse(
  bookingId: Long = 2,
  sentenceSequence: Long = 1,
  offenderNo: String = "G4803UT",
  sentenceAdjustmentId: Long = 3,
  hiddenForUsers: Boolean = false,
  prisonId: String = "MDI",
  bookingSequence: Int = 1,
): String {
  // language=JSON
  return """
{
  "bookingId":$bookingId,
  "bookingSequence":$bookingSequence,
  "id":$sentenceAdjustmentId,
  "offenderNo": "$offenderNo",
  "sentenceSequence": $sentenceSequence,
  "commentText":"a comment",
  "adjustmentDate":"2021-10-06",
  "adjustmentFromDate":"2021-10-07",
  "active":true,
  "hiddenFromUsers":$hiddenForUsers,
  "adjustmentDays":8,
  "adjustmentType": {
    "code": "RST",
    "description": "RST Desc"
  },
  "hasBeenReleased": false,
  "prisonId": "$prisonId"
  }
   
  """.trimIndent()
}
