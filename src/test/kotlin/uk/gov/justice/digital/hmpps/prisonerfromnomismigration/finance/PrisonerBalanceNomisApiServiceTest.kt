package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import java.math.BigDecimal

@SpringAPIServiceTest
@Import(PrisonerBalanceNomisApiService::class, PrisonerBalanceNomisApiMockServer::class)
class PrisonerBalanceNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: PrisonerBalanceNomisApiService

  @Autowired
  private lateinit var mockServer: PrisonerBalanceNomisApiMockServer

  @Nested
  @DisplayName("GET /finance/prisoners/ids")
  inner class GetRootOffenderIdsToMigrate {

    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetRootOffenderIdsToMigrate(1, 20)

      apiService.getRootOffenderIdsToMigrate(prisonId = null, pageNumber = 0, pageSize = 20)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will call the Nomis endpoint`() = runTest {
      mockServer.stubGetRootOffenderIdsToMigrate(1, 20)

      apiService.getRootOffenderIdsToMigrate(prisonId = null, pageNumber = 0, pageSize = 20)

      mockServer.verify(getRequestedFor(urlPathEqualTo("/finance/prisoners/ids")))
    }

    @Test
    fun `will return rootOffenderIds and paging data`() = runTest {
      mockServer.stubGetRootOffenderIdsToMigrate(3, 10)

      val rootOffenderIds = apiService.getRootOffenderIdsToMigrate(prisonId = null, pageNumber = 0, pageSize = 20)

      assertThat(rootOffenderIds.content).hasSize(3)
      assertThat(rootOffenderIds.content).containsExactly(10000, 10001, 10002)
      assertThat(rootOffenderIds.metadata.size).isEqualTo(10)
      assertThat(rootOffenderIds.metadata.number).isEqualTo(0)
      assertThat(rootOffenderIds.metadata.totalElements).isEqualTo(3)
      assertThat(rootOffenderIds.metadata.totalPages).isEqualTo(1)
    }

    @Test
    internal fun `will pass all filters when present`() = runTest {
      mockServer.stubGetRootOffenderIdsToMigrate(1, 20)

      apiService.getRootOffenderIdsToMigrate(prisonId = "BXI", pageNumber = 0, pageSize = 3)

      mockServer.verify(
        getRequestedFor(
          urlEqualTo("/finance/prisoners/ids?page=0&size=3&prisonId=BXI"),
        ),
      )
    }
  }

  @Nested
  inner class GetPrisonerBalance {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetPrisonerBalance()

      apiService.getPrisonerBalance(10000)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS root offender id to service`() = runTest {
      mockServer.stubGetPrisonerBalance()

      apiService.getPrisonerBalance(10000)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/finance/prisoners/10000/balance")),
      )
    }

    @Test
    fun `will return the prisoner balance details`() = runTest {
      mockServer.stubGetPrisonerBalance(rootOffenderId = 10000, prisonNumber = "A0001BC")

      val prisonerBalance = apiService.getPrisonerBalance(10000)

      assertThat(prisonerBalance.rootOffenderId).isEqualTo(10000)
      assertThat(prisonerBalance.prisonNumber).isEqualTo("A0001BC")
      assertThat(prisonerBalance.accounts[0].balance).isEqualTo(BigDecimal.valueOf(23.50))
      assertThat(prisonerBalance.accounts[0].holdBalance).isEqualTo(BigDecimal.valueOf(1.25))
      assertThat(prisonerBalance.accounts[0].prisonId).isEqualTo("ASI")
      assertThat(prisonerBalance.accounts[0].accountCode).isEqualTo(2101)
      assertThat(prisonerBalance.accounts[0].lastTransactionId).isEqualTo(173)
    }
  }
}
