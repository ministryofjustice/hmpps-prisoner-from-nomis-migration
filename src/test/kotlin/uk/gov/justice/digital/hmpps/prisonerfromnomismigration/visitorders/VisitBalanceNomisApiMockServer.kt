package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitorders

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerVisitOrderBalanceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate

@Component
class VisitBalanceNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetVisitBalance(
    prisonNumber: String = "A1234BC",
    visitBalance: PrisonerVisitOrderBalanceResponse = visitBalance(prisonNumber = prisonNumber),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$prisonNumber/visit-orders/balance")).willReturn(
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

fun visitBalance(prisonNumber: String = "A1234BC"): PrisonerVisitOrderBalanceResponse = PrisonerVisitOrderBalanceResponse(
  remainingPrivilegedVisitOrders = 2,
  remainingVisitOrders = 3,
  visitOrderBalanceAdjustments = listOf(
    VisitBalanceAdjustmentResponse(
      remainingVisitOrders = 6,
      remainingPrivilegedVisitOrders = 2,
      previousRemainingVisitOrders = 4,
      previousRemainingPrivilegedVisitOrders = 2,
      adjustmentReason = CodeDescription("IEP", "IEP Entitlement"),
      adjustmentDate = LocalDate.of(2024, 11, 11),
      comment = "Adj 4",
      expiryBalance = 2,
      expiryDate = LocalDate.of(2024, 12, 9),
      endorsedStaffId = 1,
      authorisedStaffId = 1,

    ),
    VisitBalanceAdjustmentResponse(
      remainingVisitOrders = -1,
      remainingPrivilegedVisitOrders = 8,
      previousRemainingVisitOrders = null,
      previousRemainingPrivilegedVisitOrders = null,
      adjustmentReason = CodeDescription("VO_ISSUE", "Visit Order of type VO is issued."),
      adjustmentDate = LocalDate.of(2024, 11, 4),
      comment = "Adj 3",
      expiryBalance = null,
      expiryDate = null,
      endorsedStaffId = 234,
      authorisedStaffId = 652,
    ),
    VisitBalanceAdjustmentResponse(
      remainingVisitOrders = null,
      remainingPrivilegedVisitOrders = null,
      previousRemainingVisitOrders = -2,
      previousRemainingPrivilegedVisitOrders = 6,
      adjustmentReason = CodeDescription("AUTO_EXP", "Automatic Expiry"),
      adjustmentDate = LocalDate.of(2024, 10, 29),
      comment = "Adj 2",
      expiryBalance = -2,
      expiryDate = null,
      endorsedStaffId = 1,
      authorisedStaffId = 1,
    ),
    VisitBalanceAdjustmentResponse(
      remainingVisitOrders = 1,
      remainingPrivilegedVisitOrders = 6,
      previousRemainingVisitOrders = 2,
      previousRemainingPrivilegedVisitOrders = 4,
      adjustmentReason = CodeDescription("IEP", "IEP Entitlement"),
      adjustmentDate = LocalDate.of(2024, 10, 28),
      comment = "Adj 1",
      expiryBalance = 2,
      expiryDate = LocalDate.of(2024, 11, 25),
      endorsedStaffId = 1,
      authorisedStaffId = 1,
    ),
  ),
)
