package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest

@SpringAPIServiceTest
@Import(VisitBalanceNomisApiService::class, VisitBalanceNomisApiMockServer::class)
class VisitBalanceNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: VisitBalanceNomisApiService

  @Autowired
  private lateinit var mockServer: VisitBalanceNomisApiMockServer

  @Nested
  inner class GetPerson {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetVisitBalance(nomisVisitBalanceId = 10000)

      apiService.getVisitBalance(visitBalanceId = 10000)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetVisitBalance(nomisVisitBalanceId = 10000)

      apiService.getVisitBalance(visitBalanceId = 10000)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/visit-balances/10000")),
      )
    }

    @Test
    fun `will return the visit balance details`() = runTest {
      mockServer.stubGetVisitBalance(nomisVisitBalanceId = 10000, prisonNumber = "A0001BC")

      val visitBalance = apiService.getVisitBalance(visitBalanceId = 10000)

      assertThat(visitBalance.remainingVisitOrders).isEqualTo(3)
      assertThat(visitBalance.remainingPrivilegedVisitOrders).isEqualTo(2)
      assertThat(visitBalance.prisonNumber).isEqualTo("A0001BC")
      assertThat(visitBalance.lastIEPAllocationDate).isEqualTo("2020-01-01")
    }

    @Test
    fun `will throw error when person does not exist`() = runTest {
      mockServer.stubGetVisitBalance(nomisVisitBalanceId = 10000, status = HttpStatus.NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getVisitBalance(visitBalanceId = 10000)
      }
    }
  }
}
