package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceDetailResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceResponse
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

  fun stubGetVisitBalanceDetail(
    nomisVisitBalanceId: Long = 12345L,
    prisonNumber: String = "A0001BC",
    visitBalance: VisitBalanceDetailResponse = visitBalanceDetail(prisonNumber = prisonNumber),
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

  fun stubGetVisitBalanceAdjustment(
    nomisVisitBalanceAdjustmentId: Long = 12345L,
    visitBalanceAdjustment: VisitBalanceAdjustmentResponse = visitBalanceAdjustment(),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/visit-balances/visit-balance-adjustment/$nomisVisitBalanceAdjustmentId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            objectMapper.writeValueAsString(visitBalanceAdjustment),
          ),
      ),
    )
  }

  fun stubGetVisitBalanceForPrisoner(
    prisonNumber: String = "A1234BC",
    response: VisitBalanceResponse = VisitBalanceResponse(
      remainingVisitOrders = 24,
      remainingPrivilegedVisitOrders = 3,
    ),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$prisonNumber/visit-orders/balance")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubGetVisitBalanceForPrisoner(
    prisonNumber: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$prisonNumber/visit-orders/balance")).willReturn(
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

fun visitBalanceDetail(prisonNumber: String = "A1234BC"): VisitBalanceDetailResponse = VisitBalanceDetailResponse(
  prisonNumber = prisonNumber,
  remainingPrivilegedVisitOrders = 2,
  remainingVisitOrders = 3,
  lastIEPAllocationDate = LocalDate.parse("2020-01-01"),
)

fun visitBalanceAdjustment(): VisitBalanceAdjustmentResponse = VisitBalanceAdjustmentResponse(
  adjustmentReason = CodeDescription(
    code = "IEP",
    description = "Incentive Earned Privilege",
  ),
  adjustmentDate = LocalDate.parse("2025-01-01"),
  createUsername = "FRED_ADM",
  visitOrderChange = 2,
  previousVisitOrderCount = 12,
  privilegedVisitOrderChange = 1,
  previousPrivilegedVisitOrderCount = 4,
  comment = "Some comment",
  expiryBalance = 5,
  expiryDate = LocalDate.parse("2025-08-01"),
  endorsedStaffId = 1234,
  authorisedStaffId = 5432,
)
