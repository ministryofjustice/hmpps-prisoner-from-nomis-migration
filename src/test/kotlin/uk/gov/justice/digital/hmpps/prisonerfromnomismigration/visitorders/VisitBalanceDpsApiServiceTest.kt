package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitorders

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerMigrationDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitorders.VisitBalanceDpsApiExtension.Companion.dpsVisitBalanceServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitorders.VisitBalanceDpsApiMockServer.Companion.visitBalanceMigrationDto
import java.time.LocalDate

@SpringAPIServiceTest
@Import(VisitBalanceDpsApiService::class, VisitBalanceConfiguration::class)
class VisitBalanceDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: VisitBalanceDpsApiService

  @Nested
  inner class MigrateVisitBalance {
    @Test
    internal fun `will pass oath2 token to migrate endpoint`() = runTest {
      dpsVisitBalanceServer.stubMigrateVisitBalance()

      apiService.migrateVisitBalance(visitBalanceMigrationDto())

      dpsVisitBalanceServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will migrate request data  to migrate endpoint`() = runTest {
      dpsVisitBalanceServer.stubMigrateVisitBalance()

      apiService.migrateVisitBalance(visitBalanceMigrationDto())

      dpsVisitBalanceServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerId", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("voBalance", equalTo("2")))
          .withRequestBody(matchingJsonPath("pvoBalance", equalTo("3")))
          .withRequestBody(matchingJsonPath("lastVoAllocationDate", equalTo("2024-03-04"))),
      )
    }

    @Test
    fun `will call the migrate endpoint`() = runTest {
      dpsVisitBalanceServer.stubMigrateVisitBalance()

      apiService.migrateVisitBalance(
        VisitAllocationPrisonerMigrationDto(
          prisonerId = "A1234BC",
          voBalance = 2,
          pvoBalance = 3,
          lastVoAllocationDate = LocalDate.of(2024, 3, 4),
        ),
      )

      dpsVisitBalanceServer.verify(
        postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/migrate")),
      )
    }
  }
}
