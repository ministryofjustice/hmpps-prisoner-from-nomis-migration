package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.scheduledpayments

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
import java.util.UUID

class ScheduledPaymentsDataRepairResourceIntTest(
  @Autowired private val nomisApiMockServer: ScheduledPaymentsNomisApiMockServer,
  @Autowired private val mappingApiMockServer: ScheduledPaymentsMappingApiMockServer,
) : FinanceIntegrationTestBase() {

  @DisplayName("POST /prisoners/scheduled-payments/{scheduledPaymentId}/repair")
  @Nested
  inner class RepairPrisonerScheduledPayment {
    val scheduledPaymentId = 12345L
    val scheduledPaymentDpsId = UUID.randomUUID()

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/scheduled-payments/$scheduledPaymentId/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/scheduled-payments/$scheduledPaymentId/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/scheduled-payments/$scheduledPaymentId/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetScheduledPayment()
        financeApi.stubCreateScheduledPayment(subscriptionId = scheduledPaymentDpsId)
        mappingApiMockServer.stubReplaceMapping()

        webTestClient.post().uri("/prisoners/scheduled-payments/$scheduledPaymentId/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will retrieve current prisonerScheduledPayment for the prisoner from Nomis`() {
        nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/finance/scheduled-payments/$scheduledPaymentId")))
      }

      @Test
      fun `will send prisonerScheduledPayment to DPS`() {
        financeApi.verify(
          postRequestedFor(urlPathEqualTo("/sync/subscriptions"))
            .withRequestBodyJsonPath("legacyPaymentProfileId", equalTo(scheduledPaymentId.toString()))
            .withRequestBodyJsonPath("prisonNumber", equalTo("A0001BC"))
            .withRequestBodyJsonPath("prisonId", equalTo("LEI"))
            .withRequestBodyJsonPath("amount", equalTo("2.1"))
            .withRequestBodyJsonPath("dayOfPayment", equalTo("WEDNESDAY"))
            .withRequestBodyJsonPath("startDate", equalTo("2024-06-18"))
            .withRequestBodyJsonPath("endDate", equalTo("2024-07-30"))
            .withRequestBodyJsonPath("subAccountCode", equalTo("2103"))
            .withRequestBodyJsonPath("subscriptionType", equalTo("TELE"))
            .withRequestBodyJsonPath("reference", equalTo("description of the scheduled payment"))
            .withRequestBodyJsonPath("createdBy", equalTo("JD12345")),
        )
      }

      @Test
      fun `will replace scheduledPayment mapping`() {
        mappingApiMockServer.verify(
          postRequestedFor(urlPathEqualTo("/mapping/scheduled-payments/replace"))
            .withRequestBodyJsonPath("nomisScheduledPaymentId", equalTo(scheduledPaymentId.toString()))
            .withRequestBodyJsonPath("dpsId", equalTo(scheduledPaymentDpsId.toString()))
            .withRequestBodyJsonPath("mappingType", equalTo("NOMIS_CREATED")),
        )
      }

      @Test
      fun `will track telemetry for the repair`() {
        verify(telemetryClient).trackEvent(
          eq("finance-scheduled-payments-resynchronisation-repair"),
          check {
            assertThat(it["scheduledPaymentId"]).isEqualTo(scheduledPaymentId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class ScheduledPaymentNotFound {
      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetScheduledPaymentNotFound(99999)

        webTestClient.post().uri("/prisoners/scheduled-payments/99999/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNotFound
          .expectBody()
          .jsonPath("userMessage").isEqualTo("Not Found: scheduledPaymentId 99999 not found")
      }

      @Test
      fun `will try to get the scheduled payment from Nomis`() {
        nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/finance/scheduled-payments/99999")))
      }

      @Test
      fun `will not send the scheduled payment to DPS`() {
        financeApi.verify(0, postRequestedFor(anyUrl()))
      }

      @Test
      fun `will not replace scheduled payment mapping`() {
        mappingApiMockServer.verify(0, postRequestedFor(urlPathEqualTo("/mapping/scheduled-payments/replace")))
      }

      @Test
      fun `will not track telemetry for the repair`() {
        verifyNoInteractions(telemetryClient)
      }
    }
  }
}
