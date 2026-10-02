package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.holds

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.offenderTransactionDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.HoldDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath

class HoldsDataRepairResourceIntTest(
  @Autowired private val nomisApiMockServer: FinanceNomisApiMockServer,
) : FinanceIntegrationTestBase() {
  companion object {
    internal const val BOOKING_ID = 1234L
    internal const val NOMIS_TRANSACTION_ID = 2345678L
  }

  @DisplayName("POST /holds/{transactionId}/repair")
  @Nested
  inner class RepairHoldsOffenderId {
    val transactionId = 12345L

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/holds/$transactionId/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/holds/$transactionId/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/holds/$transactionId/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {

      val holdTransaction = offenderTransactionDto(bookingId = BOOKING_ID, transactionId = NOMIS_TRANSACTION_ID).copy(
        type = "HOA",
        holdDetails = HoldDto(
          holdNumber = 65432,
          holdCleared = false,
        ),
      )

      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetPrisonerTransaction(
          bookingId = BOOKING_ID,
          transactionId = NOMIS_TRANSACTION_ID,
          response = listOf(holdTransaction),
        )
        financeApi.stubMigrateHold()

        webTestClient.post().uri("/holds/$NOMIS_TRANSACTION_ID/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will retrieve current transaction from Nomis`() {
        nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/transactions/$NOMIS_TRANSACTION_ID")))
      }

      @Test
      fun `will send the hold to DPS`() {
        val g1 = holdTransaction.generalLedgerTransactions.first()

        financeApi.verify(
          postRequestedFor(urlPathEqualTo("/migrate/holds"))
            .withRequestBodyJsonPath("prisonNumber", holdTransaction.offenderNo)
            .withRequestBodyJsonPath("subAccountCode", 2101)
            .withRequestBodyJsonPath("holdNumber", holdTransaction.holdDetails!!.holdNumber!!)
            .withRequestBodyJsonPath("holdTransactionId", holdTransaction.transactionId)
            .withRequestBodyJsonPath("isReleased", holdTransaction.holdDetails.holdCleared)
            .withRequestBodyJsonPath("description", holdTransaction.description)
            .withRequestBodyJsonPath("holdType", holdTransaction.type)
            .withRequestBodyJsonPath("holdLocation", holdTransaction.caseloadId)
            .withRequestBodyJsonPath("amount", holdTransaction.amount)
            .withRequestBodyJsonPath("holdFromDate", g1.transactionTimestamp)
            .withRequestBodyJsonPath("createdAt", holdTransaction.createdAt)
            .withRequestBodyJsonPath("createdBy", holdTransaction.createdBy),
        )
      }

      @Test
      fun `will track telemetry for the repair`() {
        verify(telemetryClient).trackEvent(
          eq("hold-resynchronisation-repair"),
          check {
            assertThat(it["transactionId"]).isEqualTo(NOMIS_TRANSACTION_ID.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class HappyPathNotFound {
      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetPrisonerTransactionNotFound(transactionId)
        financeApi.stubMigrateHold()

        webTestClient.post().uri("/holds/$transactionId/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNotFound
          .expectBody()
          .jsonPath("userMessage").isEqualTo("Not Found: No hold transaction for 12345 was found")
      }

      @Test
      fun `will try to retrieve current transaction from Nomis`() {
        nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/transactions/$transactionId")))
      }

      @Test
      fun `will not send hold to DPS`() {
        financeApi.verify(0, postRequestedFor(anyUrl()))
      }

      @Test
      fun `will not track telemetry for the repair`() {
        verifyNoInteractions(telemetryClient)
      }
    }
  }
}
