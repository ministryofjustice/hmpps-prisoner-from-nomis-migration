package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceDetailResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate

@Component
class VisitBalanceNomisApiMockServer(private val jsonMapper: JsonMapper) {
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
            jsonMapper.writeValueAsString(visitBalance),
          ),
      ),
    )
  }

  fun stubGetVisitBalanceDetailForPrisoner(
    prisonNumber: String = "A0001BC",
    visitBalance: VisitBalanceDetailResponse = visitBalanceDetail(prisonNumber = prisonNumber),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$prisonNumber/visit-balance/details")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            jsonMapper.writeValueAsString(visitBalance),
          ),
      ),
    )
  }

  fun stubGetVisitBalanceDetailForPrisonerNotFound(
    prisonNumber: String = "A0001BC",
    status: HttpStatus = HttpStatus.NOT_FOUND,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$prisonNumber/visit-balance/details")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(
            jsonMapper.writeValueAsString(error),
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
            jsonMapper.writeValueAsString(visitBalanceAdjustment),
          ),
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
  latestBooking = true,
)
