package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

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
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances.VisitBalanceDpsApiExtension.Companion.dpsVisitBalanceServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class VisitBalanceDataRepairResourceIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var visitBalanceNomisApiMockServer: VisitBalanceNomisApiMockServer

  @DisplayName("POST /prisoners/{prisonNumber}/visit-balance/repair")
  @Nested
  inner class RepairVisitBalance {
    val prisonNumber = "A1234KT"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/$prisonNumber/visit-balance/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/$prisonNumber/visit-balance/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/$prisonNumber/visit-balance/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      val prisonNumber = "A1234KT"

      @BeforeEach
      fun setUp() {
        visitBalanceNomisApiMockServer.stubGetVisitBalanceDetailForPrisoner(prisonNumber)
        dpsVisitBalanceServer.stubMigrateVisitBalance()

        webTestClient.post().uri("/prisoners/$prisonNumber/visit-balance/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISIT_BALANCE")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will retrieve current visitBalance for the prisoner`() {
        visitBalanceNomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/prisoners/$prisonNumber/visit-balance/details")))
      }

      @Test
      fun `will send visitBalance to DPS`() {
        dpsVisitBalanceServer.verify(
          postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/migrate"))
            .withRequestBodyJsonPath("prisonerId", prisonNumber)
            .withRequestBodyJsonPath("voBalance", "3")
            .withRequestBodyJsonPath("pvoBalance", "2")
            .withRequestBodyJsonPath("lastVoAllocationDate", "2020-01-01"),
        )
      }

      @Test
      fun `will track telemetry for the repair`() {
        verify(telemetryClient).trackEvent(
          eq("visitbalance-resynchronisation-repair"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathNotFound {
      val prisonNumber = "A1234KT"

      @BeforeEach
      fun setUp() {
        visitBalanceNomisApiMockServer.stubGetVisitBalanceDetailForPrisonerNotFound(prisonNumber)
        dpsVisitBalanceServer.stubMigrateVisitBalance()

        webTestClient.post().uri("/prisoners/$prisonNumber/visit-balance/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_VISIT_BALANCE")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will retrieve current visitBalance for the prisoner`() {
        visitBalanceNomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/prisoners/$prisonNumber/visit-balance/details")))
      }

      @Test
      fun `will send visitBalance to DPS`() {
        dpsVisitBalanceServer.verify(
          postRequestedFor(urlPathEqualTo("/visits/allocation/prisoner/migrate"))
            .withRequestBodyJsonPath("prisonerId", prisonNumber)
            .withRequestBodyJsonPath("voBalance", "0")
            .withRequestBodyJsonPath("pvoBalance", "0")
            .withRequestBodyJsonPath("lastVoAllocationDate", LocalDate.now().minusDays(14).format(DateTimeFormatter.ISO_DATE)),
        )
      }

      @Test
      fun `will track telemetry for the repair`() {
        verify(telemetryClient).trackEvent(
          eq("visitbalance-resynchronisation-repair"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
          },
          isNull(),
        )
      }
    }
  }
}
