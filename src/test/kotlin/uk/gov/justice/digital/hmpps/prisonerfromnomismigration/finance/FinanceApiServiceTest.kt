package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension.Companion.financeApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiMockServer.Companion.prisonerBalanceMigrationDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath

@SpringAPIServiceTest
@Import(FinanceApiService::class, FinanceConfiguration::class)
class FinanceApiServiceTest {
  @Autowired
  private lateinit var apiService: FinanceApiService

  @Nested
  inner class MigratePrisonerBalance {
    @Test
    internal fun `will pass oath2 token to migrate endpoint`() = runTest {
      financeApi.stubMigratePrisonerBalance()

      apiService.migratePrisonerBalance("A1234BC", prisonerBalanceMigrationDto())

      financeApi.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will migrate request data  to migrate endpoint`() = runTest {
      financeApi.stubMigratePrisonerBalance()

      apiService.migratePrisonerBalance("A1234BC", prisonerBalanceMigrationDto())

      financeApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBodyJsonPath("initialBalances[0].accountCode", equalTo("2101"))
          .withRequestBodyJsonPath("initialBalances[0].balance", equalTo("23.5"))
          .withRequestBodyJsonPath("initialBalances[0].holdBalance", equalTo("1.25"))
          .withRequestBodyJsonPath("initialBalances[1].accountCode", equalTo("2102"))
          .withRequestBodyJsonPath("initialBalances[1].balance", equalTo("11.5"))
          .withRequestBodyJsonPath("initialBalances[1].holdBalance", equalTo("0")),
      )
    }

    @Test
    fun `will call the migrate endpoint`() = runTest {
      financeApi.stubMigratePrisonerBalance()

      apiService.migratePrisonerBalance("A1234BC", prisonerBalanceMigrationDto())

      financeApi.verify(
        postRequestedFor(urlPathEqualTo("/migrate/prisoner-balances/A1234BC")),
      )
    }
  }
}
