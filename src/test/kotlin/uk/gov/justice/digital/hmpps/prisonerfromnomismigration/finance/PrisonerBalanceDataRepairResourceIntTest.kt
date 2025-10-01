package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension.Companion.financeApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath

class PrisonerBalanceDataRepairResourceIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMockServer: PrisonerBalanceNomisApiMockServer

  @DisplayName("POST /prisoners/{rootOffenderId}/prisoner-balance/repair")
  @Nested
  inner class RepairPrisonerBalance {
    val rootOffenderId = 12345L

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/$rootOffenderId/prisoner-balance/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/$rootOffenderId/prisoner-balance/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/$rootOffenderId/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      val rootOffenderId: Long = 12345
      val prisonNumber = "A1234BC"

      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetPrisonerBalance(rootOffenderId = rootOffenderId, prisonNumber = prisonNumber)
        financeApi.stubMigratePrisonerBalance()

        webTestClient.post().uri("/prisoners/$rootOffenderId/prisoner-balance/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_NOMIS_SYSCON")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will retrieve current prisonerBalance for the prisoner`() {
        nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/finance/prisoners/$rootOffenderId/balance")))
      }

      @Test
      fun `will send prisonerBalance to DPS`() {
        financeApi.verify(
          postRequestedFor(urlPathEqualTo("/migrate/prisoner-balances/$prisonNumber"))
            .withRequestBodyJsonPath("accountBalances[0].accountCode", 2101)
            .withRequestBodyJsonPath("accountBalances[0].prisonId", "ASI")
            .withRequestBodyJsonPath("accountBalances[0].balance", 23.5)
            .withRequestBodyJsonPath("accountBalances[0].holdBalance", 1.25),
        )
      }

      @Test
      fun `will track telemetry for the repair`() {
        verify(telemetryClient).trackEvent(
          eq("prisonerbalance-resynchronisation-repair"),
          check {
            assertThat(it["rootOffenderId"]).isEqualTo(rootOffenderId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathNotFound {
      val rootOffenderId = 12345L
      val prisonNumber = "A1234BC"

      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetPrisonerBalanceNotFound(rootOffenderId)
        financeApi.stubMigratePrisonerBalance()

        webTestClient.post().uri("/prisoners/$rootOffenderId/prisoner-balance/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_NOMIS_SYSCON")))
          .exchange()
          .expectStatus().isNotFound
          .expectBody()
          .jsonPath("userMessage").isEqualTo("Not Found: No prisoner balance for 12345 was found")
      }

      @Test
      fun `will try to retrieve current prisonerBalance for the prisoner`() {
        nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/finance/prisoners/$rootOffenderId/balance")))
      }

      @Test
      fun `will not send prisonerBalance to DPS`() {
        financeApi.verify(0, getRequestedFor(anyUrl()))
      }

      @Test
      fun `will not track telemetry for the repair`() {
        verifyNoInteractions(telemetryClient)
      }
    }
  }
}
