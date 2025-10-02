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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiMockServer.Companion.prisonBalanceMigrationDto
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
          .withRequestBodyJsonPath("accountBalances[0].prisonId", equalTo("ASI"))
          .withRequestBodyJsonPath("accountBalances[0].accountCode", equalTo("2101"))
          .withRequestBodyJsonPath("accountBalances[0].balance", equalTo("23.5"))
          .withRequestBodyJsonPath("accountBalances[0].holdBalance", equalTo("1.25"))
          .withRequestBodyJsonPath("accountBalances[0].transactionId", equalTo("173"))
          .withRequestBodyJsonPath("accountBalances[0].asOfTimestamp", equalTo("2025-06-02T02:02:03"))
          .withRequestBodyJsonPath("accountBalances[1].prisonId", equalTo("ASI"))
          .withRequestBodyJsonPath("accountBalances[1].accountCode", equalTo("2102"))
          .withRequestBodyJsonPath("accountBalances[1].balance", equalTo("11.5"))
          .withRequestBodyJsonPath("accountBalances[1].holdBalance", equalTo("0"))
          .withRequestBodyJsonPath("accountBalances[1].transactionId", equalTo("174"))
          .withRequestBodyJsonPath("accountBalances[1].asOfTimestamp", equalTo("2025-06-01T01:02:03")),
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

  @Nested
  inner class MigratePrisonBalance {
    @Test
    internal fun `will pass oath2 token to migrate endpoint`() = runTest {
      financeApi.stubMigratePrisonBalance()

      apiService.migratePrisonBalance("MDI", prisonBalanceMigrationDto())

      financeApi.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will migrate request data  to migrate endpoint`() = runTest {
      financeApi.stubMigratePrisonBalance()

      apiService.migratePrisonBalance("MDI", prisonBalanceMigrationDto())

      financeApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBodyJsonPath("accountBalances[0].accountCode", equalTo("2101"))
          .withRequestBodyJsonPath("accountBalances[0].balance", equalTo("23.5"))
          .withRequestBodyJsonPath("accountBalances[1].accountCode", equalTo("2102"))
          .withRequestBodyJsonPath("accountBalances[1].balance", equalTo("11.5")),
      )
    }

    @Test
    fun `will call the migrate endpoint`() = runTest {
      financeApi.stubMigratePrisonBalance()

      apiService.migratePrisonBalance("MDI", prisonBalanceMigrationDto())

      financeApi.verify(
        postRequestedFor(urlPathEqualTo("/migrate/general-ledger-balances/MDI")),
      )
    }
  }
}
