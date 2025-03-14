package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerVisitBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDate

@Component
class VisitBalanceNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetVisitBalanceIds(totalElements: Long = 20, pageSize: Long = 20, firstVisitBalanceId: Long = 10000L) {
    val content: List<VisitBalanceIdResponse> = (1..kotlin.math.min(pageSize, totalElements)).map {
      VisitBalanceIdResponse(visitBalanceId = firstVisitBalanceId + it - 1)
    }
    nomisApi.stubFor(
      get(urlPathEqualTo("/visit-balances/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            pageContent(
              objectMapper = NomisApiExtension.Companion.objectMapper,
              content = content,
              pageSize = pageSize,
              pageNumber = 0,
              totalElements = totalElements,
              size = pageSize.toInt(),
            ),
          ),
      ),
    )
  }

  fun stubGetVisitBalance(
    nomisVisitBalanceId: Long = 12345L,
    prisonNumber: String = "A0001BC",
    visitBalance: PrisonerVisitBalanceResponse = visitBalance(prisonNumber = prisonNumber),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/visit-balances/$nomisVisitBalanceId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            objectMapper.writeValueAsString(visitBalance),
          ),
      ),
    )
  }

  fun stubGetVisitBalance(
    nomisVisitBalanceId: Long = 12345L,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/visit-balances/$nomisVisitBalanceId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun visitBalance(prisonNumber: String = "A1234BC"): PrisonerVisitBalanceResponse = PrisonerVisitBalanceResponse(
  prisonNumber = prisonNumber,
  remainingPrivilegedVisitOrders = 2,
  remainingVisitOrders = 3,
  lastIEPAllocationDate = LocalDate.parse("2020-01-01"),
)
