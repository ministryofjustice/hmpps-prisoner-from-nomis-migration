package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi

@Component
class CalculateReleaseDatesNomisApiMockServer {
  fun stubGetSentenceCalculation(
    offenderSentenceCalculationId: Long,
    response: String = sentenceCalculationJson(id = offenderSentenceCalculationId),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/sentence-calculations/$offenderSentenceCalculationId")).willReturn(okJson(response)),
    )
  }

  fun stubGetSentenceCalculationNotFound(offenderSentenceCalculationId: Long) {
    nomisApi.stubFor(
      get(urlEqualTo("/sentence-calculations/$offenderSentenceCalculationId"))
        .willReturn(nomisApiNotFound()),
    )
  }

  fun stubGetSentenceCalculationsForPrisoner(
    offenderNo: String,
    response: String = "[${sentenceCalculationJson(offenderNo = offenderNo)}]",
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/sentence-calculations")).willReturn(okJson(response)),
    )
  }

  fun stubGetSentenceCalculationsForPrisonerNotFound(offenderNo: String) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/sentence-calculations")).willReturn(nomisApiNotFound()),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
}

private fun nomisApiNotFound() = com.github.tomakehurst.wiremock.client.WireMock.aResponse()
  .withStatus(404)
  .withHeader("Content-Type", "application/json")
  .withBody("""{"status":404,"userMessage":"Not found"}""")

fun sentenceCalculationJson(
  id: Long = 1234567,
  bookingId: Long = 123456,
  offenderNo: String = "A1234KT",
  calculationReasonCode: String = "NEW",
  crdCalculatedDate: String? = "2030-01-02",
  sedCalculatedDate: String? = "2031-02-03",
) = """
  {
    "id": $id,
    "bookingId": $bookingId,
    "offenderNo": "$offenderNo",
    "calculationDate": "2024-01-02T03:04:05",
    "calculationReasonCode": "$calculationReasonCode",
    "crdCalculatedDate": ${crdCalculatedDate?.let { "\"$it\"" } ?: "null"},
    "sedCalculatedDate": ${sedCalculatedDate?.let { "\"$it\"" } ?: "null"},
    "recordDatetime": "2024-01-02T03:04:05",
    "createDatetime": "2024-01-02T03:04:05",
    "createUsername": "NOMIS.USER"
  }
""".trimIndent()
