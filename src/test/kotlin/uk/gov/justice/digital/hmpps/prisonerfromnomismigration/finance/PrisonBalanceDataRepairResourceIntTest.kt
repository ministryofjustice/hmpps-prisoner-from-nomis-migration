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

class PrisonBalanceDataRepairResourceIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMockServer: FinanceNomisApiMockServer

  @DisplayName("POST /prisons/{prisonId}/prison-balance/repair")
  @Nested
  inner class RepairPrisonerBalance {
    val prisonId = "MDI"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisons/$prisonId/prison-balance/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisons/$prisonId/prison-balance/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisons/$prisonId/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      val prisonId = "MDI"

      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetPrisonBalance(prisonId = prisonId)
        financeApi.stubMigratePrisonBalance()

        webTestClient.post().uri("/prisons/$prisonId/prison-balance/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_NOMIS_SYSCON")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will retrieve current prison balance for the prison`() {
        nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/finance/prison/$prisonId/balance")))
      }

      @Test
      fun `will send prison balance to DPS`() {
        financeApi.verify(
          postRequestedFor(urlPathEqualTo("/migrate/general-ledger-balances/$prisonId"))
            .withRequestBodyJsonPath("accountBalances[0].accountCode", 2101)
            .withRequestBodyJsonPath("accountBalances[0].balance", "23.45")
            .withRequestBodyJsonPath("accountBalances[0].asOfTimestamp", "2025-06-02T02:02:03"),
        )
      }

      @Test
      fun `will track telemetry for the repair`() {
        verify(telemetryClient).trackEvent(
          eq("prisonbalance-resynchronisation-repair"),
          check {
            assertThat(it["prisonId"]).isEqualTo(prisonId)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathNotFound {
      val prisonId = "MDI"

      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetPrisonBalanceNotFound(prisonId)
        financeApi.stubMigratePrisonBalance()

        webTestClient.post().uri("/prisons/$prisonId/prison-balance/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_NOMIS_SYSCON")))
          .exchange()
          .expectStatus().isNotFound
          .expectBody()
          .jsonPath("userMessage").isEqualTo("Not Found: No prison balance for MDI was found")
      }

      @Test
      fun `will try to retrieve current prison balance for the prison`() {
        nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/finance/prison/$prisonId/balance")))
      }

      @Test
      fun `will not send prison balance to DPS`() {
        financeApi.verify(0, getRequestedFor(anyUrl()))
      }

      @Test
      fun `will not track telemetry for the repair`() {
        verifyNoInteractions(telemetryClient)
      }
    }
  }
}
