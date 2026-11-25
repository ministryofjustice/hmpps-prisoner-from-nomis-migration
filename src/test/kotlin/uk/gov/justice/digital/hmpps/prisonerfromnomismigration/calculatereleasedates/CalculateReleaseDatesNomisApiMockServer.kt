package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderSentenceCalculationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class CalculateReleaseDatesNomisApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    fun sentenceCalculationResponse(
      id: Long = 1234567,
      bookingId: Long = 123456,
      offenderNo: String = "A1234KT",
    ) = OffenderSentenceCalculationResponse(
      id = id,
      bookingId = bookingId,
      offenderNo = offenderNo,
      calculationDate = LocalDateTime.parse("2024-01-02T03:04:05"),
      calculationReasonCode = "NEW",
      crdCalculatedDate = LocalDate.parse("2030-01-02"),
      sedCalculatedDate = LocalDate.parse("2031-02-03"),
      recordDatetime = LocalDateTime.parse("2024-01-02T03:04:05"),
      createDatetime = LocalDateTime.parse("2024-01-02T03:04:05"),
      createUsername = "NOMIS.USER",
    )
  }

  fun stubGetSentenceCalculation(
    offenderSentenceCalculationId: Long = 1234567,
    response: OffenderSentenceCalculationResponse = sentenceCalculationResponse(id = offenderSentenceCalculationId),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/sentence-calculations/$offenderSentenceCalculationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetSentenceCalculationNotFound(offenderSentenceCalculationId: Long) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/sentence-calculations/$offenderSentenceCalculationId")).willReturn(notFound()),
    )
  }

  fun stubGetSentenceCalculationsForPrisoner(
    offenderNo: String = "A1234KT",
    response: List<OffenderSentenceCalculationResponse> = listOf(sentenceCalculationResponse(offenderNo = offenderNo)),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/sentence-calculations")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetSentenceCalculationsForPrisonerNotFound(offenderNo: String) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/sentence-calculations")).willReturn(notFound()),
    )
  }

  private fun notFound() = aResponse()
    .withStatus(HttpStatus.NOT_FOUND.value())
    .withHeader("Content-Type", "application/json")
    .withBody("""{"status":404,"userMessage":"Not found"}""")

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
}
