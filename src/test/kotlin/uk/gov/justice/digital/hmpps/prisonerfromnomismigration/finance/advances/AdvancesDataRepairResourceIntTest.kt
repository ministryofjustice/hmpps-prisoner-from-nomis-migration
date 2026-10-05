package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath

class AdvancesDataRepairResourceIntTest(
  @Autowired private val nomisApiMockServer: AdvancesNomisApiMockServer,
) : FinanceIntegrationTestBase() {

  @DisplayName("POST /prisoners/advances/{advanceId}/repair")
  @Nested
  inner class RepairPrisonerAdvance {
    val advanceId = 12345L

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/advances/$advanceId/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/advances/$advanceId/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/advances/$advanceId/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetAdvance()
        financeApi.stubSyncAdvance()

        webTestClient.post().uri("/prisoners/advances/$advanceId/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will retrieve current prisonerAdvance for the prisoner from Nomis`() {
        nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/finance/prisoners/advances/$advanceId")))
      }

      @Test
      fun `will send prisonerAdvance to DPS`() {
        financeApi.verify(
          postRequestedFor(urlPathEqualTo("/sync/advances"))
            .withRequestBodyJsonPath("legacyPaymentProfileId", equalTo("12345"))
            // TODO fix when Nomis API returns the InformationNumber
            .withRequestBodyJsonPath("legacyInformationNumber", equalTo("info-123"))
            .withRequestBodyJsonPath("prisonNumber", equalTo("A0001BC"))
            .withRequestBodyJsonPath("prisonID", equalTo("LEI"))
            .withRequestBodyJsonPath("amount", equalTo("2.1"))
            .withRequestBodyJsonPath("repaymentAmount", equalTo("0.5"))
            .withRequestBodyJsonPath("repaymentStartDate", equalTo("2024-06-18T00:00:00"))
            .withRequestBodyJsonPath("comment", equalTo("This is a comment"))
            .withRequestBodyJsonPath("reference", equalTo("description of the advance"))
            .withRequestBodyJsonPath("createdBy", equalTo("JD12345"))
            .withRequestBodyJsonPath("createdOn", equalTo("2024-06-18T12:30:45"))
            // TODO fix when Nomis API returns the status
            .withRequestBodyJsonPath("status", equalTo("ACTIVE"))
            // TODO fix when Nomis API returns the transactionId
            .withRequestBodyJsonPath("legacyTransactionId", equalTo("123")),
        )
      }

      @Test
      fun `will track telemetry for the repair`() {
        verify(telemetryClient).trackEvent(
          eq("prisoneradvance-resynchronisation-repair"),
          check {
            assertThat(it["advanceId"]).isEqualTo(advanceId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class AdvanceNotFound {
      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetAdvanceNotFound(99999)

        webTestClient.post().uri("/prisoners/advances/99999/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNotFound
          .expectBody()
          .jsonPath("userMessage").isEqualTo("Not Found: advanceId 99999 not found")
      }

      @Test
      fun `will try to get the advanceId from Nomis`() {
        nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/finance/prisoners/advances/99999")))
      }

      @Test
      fun `will not send prisonerAdvance to DPS`() {
        financeApi.verify(0, postRequestedFor(anyUrl()))
      }

      @Test
      fun `will not track telemetry for the repair`() {
        verifyNoInteractions(telemetryClient)
      }
    }
  }
}
