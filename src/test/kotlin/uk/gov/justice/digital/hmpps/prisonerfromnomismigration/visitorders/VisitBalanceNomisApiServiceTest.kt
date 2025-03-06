package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitorders

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
      mockServer.stubGetVisitBalance(prisonNumber = "A1234BC")

      apiService.getVisitBalance(prisonNumber = "A1234BC")

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetVisitBalance(prisonNumber = "A1237BC")

      apiService.getVisitBalance(prisonNumber = "A1237BC")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1237BC/visit-orders/balance")),
      )
    }

    @Test
    fun `will return the visit balance details`() = runTest {
      mockServer.stubGetVisitBalance(prisonNumber = "A1234BC")

      val visitBalance = apiService.getVisitBalance(prisonNumber = "A1234BC")

      assertThat(visitBalance.remainingVisitOrders).isEqualTo(3)
      assertThat(visitBalance.remainingPrivilegedVisitOrders).isEqualTo(2)
      assertThat(visitBalance.visitOrderBalanceAdjustments.size).isEqualTo(4)
    }

    @Test
    fun `will throw error when person does not exist`() = runTest {
      mockServer.stubGetVisitBalance(prisonNumber = "A1234BC", status = HttpStatus.NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getVisitBalance(prisonNumber = "A1234BC")
      }
    }
  }
}
