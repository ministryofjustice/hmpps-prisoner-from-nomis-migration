package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import java.time.LocalDate

@SpringAPIServiceTest
@Import(VisitBalanceNomisApiService::class, VisitBalanceNomisApiMockServer::class)
class VisitBalanceNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: VisitBalanceNomisApiService

  @Autowired
  private lateinit var mockServer: VisitBalanceNomisApiMockServer

  @Nested
  inner class GetVisitBalanceDetail {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetVisitBalanceDetail(nomisVisitBalanceId = 10000)

      apiService.getVisitBalanceDetail(visitBalanceId = 10000)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetVisitBalanceDetail(nomisVisitBalanceId = 10000)

      apiService.getVisitBalanceDetail(visitBalanceId = 10000)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/visit-balances/10000")),
      )
    }

    @Test
    fun `will return the visit balance details`() = runTest {
      mockServer.stubGetVisitBalanceDetail(nomisVisitBalanceId = 10000, prisonNumber = "A0001BC")

      val visitBalance = apiService.getVisitBalanceDetail(visitBalanceId = 10000)

      assertThat(visitBalance.remainingVisitOrders).isEqualTo(3)
      assertThat(visitBalance.remainingPrivilegedVisitOrders).isEqualTo(2)
      assertThat(visitBalance.prisonNumber).isEqualTo("A0001BC")
      assertThat(visitBalance.lastIEPAllocationDate).isEqualTo("2020-01-01")
    }
  }

  @Nested
  inner class GetVisitBalanceForPrisoner {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetVisitBalanceForPrisoner()

      apiService.getVisitBalanceForPrisoner("A1234BC")

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetVisitBalanceForPrisoner()

      apiService.getVisitBalanceForPrisoner("A1234BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234BC/visit-orders/balance")),
      )
    }

    @Test
    fun `will return the visit balance`() = runTest {
      mockServer.stubGetVisitBalanceForPrisoner()

      val visitBalance = apiService.getVisitBalanceForPrisoner("A1234BC")

      assertThat(visitBalance.remainingVisitOrders).isEqualTo(24)
      assertThat(visitBalance.remainingPrivilegedVisitOrders).isEqualTo(3)
    }
  }

  @Nested
  inner class GetVisitBalanceAdjustment {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetVisitBalanceAdjustment(nomisVisitBalanceAdjustmentId = 10000)

      apiService.getVisitBalanceAdjustment(10000)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetVisitBalanceAdjustment(nomisVisitBalanceAdjustmentId = 10000)

      apiService.getVisitBalanceAdjustment(10000)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/visit-balances/visit-balance-adjustment/10000")),
      )
    }

    @Test
    fun `will return the visit balance adjustment`() = runTest {
      mockServer.stubGetVisitBalanceAdjustment(nomisVisitBalanceAdjustmentId = 10000)

      val visitBalanceAdjustment = apiService.getVisitBalanceAdjustment(10000)

      assertThat(visitBalanceAdjustment.adjustmentReason.code).isEqualTo("IEP")
      assertThat(visitBalanceAdjustment.adjustmentDate).isEqualTo(LocalDate.parse("2025-01-01"))
      assertThat(visitBalanceAdjustment.createUsername).isEqualTo("FRED_ADM")
      assertThat(visitBalanceAdjustment.visitOrderChange).isEqualTo(2)
      assertThat(visitBalanceAdjustment.previousVisitOrderCount).isEqualTo(12)
      assertThat(visitBalanceAdjustment.privilegedVisitOrderChange).isEqualTo(1)
      assertThat(visitBalanceAdjustment.previousPrivilegedVisitOrderCount).isEqualTo(4)
      assertThat(visitBalanceAdjustment.comment).isEqualTo("Some comment")
      assertThat(visitBalanceAdjustment.expiryBalance).isEqualTo(5)
      assertThat(visitBalanceAdjustment.expiryDate).isEqualTo(LocalDate.parse("2025-08-01"))
      assertThat(visitBalanceAdjustment.endorsedStaffId).isEqualTo(1234)
      assertThat(visitBalanceAdjustment.authorisedStaffId).isEqualTo(5432)
    }
  }
}
