package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.util.UUID

class TransactionDataRepairResourceIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApiMockServer: FinanceNomisApiMockServer

  @Autowired
  private lateinit var mappingApiMockServer: FinanceMappingApiMockServer

  @DisplayName("POST /transactions/{transactionId}/repair")
  @Nested
  inner class TransactionRepair {
    val transactionId = 1234

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/transactions/$transactionId/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/transactions/$transactionId/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/transactions/$transactionId/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @Nested
      inner class HappyPathPrisonTransaction {
        val transactionId = 1234L
        val dpsTransactionId = UUID.randomUUID()

        val receipt = SyncTransactionReceipt(
          synchronizedTransactionId = dpsTransactionId,
          requestId = dpsTransactionId,
          action = SyncTransactionReceipt.Action.UPDATED,
        )

        @BeforeEach
        fun setUp() {
          nomisApiMockServer.stubGetPrisonerTransaction(transactionId = transactionId, response = emptyList())
          nomisApiMockServer.stubGetPrisonTransaction(transactionId = transactionId)
          mappingApiMockServer.stubGetByNomisId(transactionId = transactionId)
          financeApi.stubPostPrisonTransaction(receipt)

          webTestClient.post().uri("/transactions/$transactionId/repair")
            .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
            .exchange()
            .expectStatus().isNoContent
        }

        @Test
        fun `will attempt to retrieve prisoner transactions`() {
          nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/transactions/$transactionId")))
        }

        @Test
        fun `will retrieve prison transactions`() {
          nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/transactions/$transactionId/general-ledger")))
        }

        @Test
        fun `will retrieve mapping for the transaction`() {
          mappingApiMockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/transactions/nomis-transaction-id/$transactionId")))
        }

        @Test
        fun `will send the transaction to DPS`() {
          financeApi.verify(
            postRequestedFor(urlPathEqualTo("/sync/general-ledger-transactions"))
              .withRequestBodyJsonPath("transactionId", transactionId)
              .withRequestBodyJsonPath("generalLedgerEntries.size()", 1),
          )
        }

        @Test
        fun `will not attempt to save mapping for the transaction`() {
          mappingApiMockServer.verify(0, postRequestedFor(urlEqualTo("/mapping/transactions")))
        }

        @Test
        fun `will track telemetry for the repair`() {
          verify(telemetryClient).trackEvent(
            eq("transaction-resynchronisation-repair"),
            check {
              assertThat(it["transactionId"]).isEqualTo(transactionId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class HappyPathPrisonTransactionNoMapping {
        val transactionId = 1234L
        val dpsTransactionId = UUID.randomUUID()

        val receipt = SyncTransactionReceipt(
          synchronizedTransactionId = dpsTransactionId,
          requestId = dpsTransactionId,
          action = SyncTransactionReceipt.Action.UPDATED,
        )

        @BeforeEach
        fun setUp() {
          nomisApiMockServer.stubGetPrisonerTransaction(transactionId = transactionId, response = emptyList())
          nomisApiMockServer.stubGetPrisonTransaction(transactionId = transactionId)
          mappingApiMockServer.stubGetByNomisId(transactionId = transactionId, mapping = null)
          financeApi.stubPostPrisonTransaction(receipt)
          mappingApiMockServer.stubPostMapping()

          webTestClient.post().uri("/transactions/$transactionId/repair")
            .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
            .exchange()
            .expectStatus().isNoContent
        }

        @Test
        fun `will attempt to retrieve prisoner transactions`() {
          nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/transactions/$transactionId")))
        }

        @Test
        fun `will retrieve prison transactions`() {
          nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/transactions/$transactionId/general-ledger")))
        }

        @Test
        fun `will attempt to retrieve mapping for the transaction`() {
          mappingApiMockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/transactions/nomis-transaction-id/$transactionId")))
        }

        @Test
        fun `will send the transaction to DPS`() {
          financeApi.verify(
            postRequestedFor(urlPathEqualTo("/sync/general-ledger-transactions"))
              .withRequestBodyJsonPath("transactionId", transactionId)
              .withRequestBodyJsonPath("generalLedgerEntries.size()", 1),
          )
        }

        @Test
        fun `will save mapping for the transaction`() {
          mappingApiMockServer.verify(postRequestedFor(urlEqualTo("/mapping/transactions")))
        }

        @Test
        fun `will track telemetry for the repair`() {
          verify(telemetryClient).trackEvent(
            eq("transaction-resynchronisation-repair"),
            check {
              assertThat(it["transactionId"]).isEqualTo(transactionId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class HappyPathPrisonerTransaction {
        val transactionId = 1234L
        val dpsTransactionId = UUID.randomUUID()
        val receipt = SyncTransactionReceipt(
          synchronizedTransactionId = dpsTransactionId,
          requestId = dpsTransactionId,
          action = SyncTransactionReceipt.Action.UPDATED,
        )

        @BeforeEach
        fun setUp() {
          nomisApiMockServer.stubGetPrisonerTransaction(transactionId = transactionId)
          mappingApiMockServer.stubGetByNomisId(transactionId = transactionId)
          financeApi.stubPostPrisonerTransaction(receipt)

          webTestClient.post().uri("/transactions/$transactionId/repair")
            .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
            .exchange()
            .expectStatus().isNoContent
        }

        @Test
        fun `will retrieve prisoner transactions`() {
          nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/transactions/$transactionId")))
        }

        @Test
        fun `will not attempt to  retrieve prison transactions`() {
          nomisApiMockServer.verify(0, getRequestedFor(urlPathEqualTo("/transactions/$transactionId/general-ledger")))
        }

        @Test
        fun `will retrieve mapping for the transaction`() {
          mappingApiMockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/transactions/nomis-transaction-id/$transactionId")))
        }

        @Test
        fun `will send the transaction to DPS`() {
          financeApi.verify(
            postRequestedFor(urlPathEqualTo("/sync/offender-transactions"))
              .withRequestBodyJsonPath("transactionId", transactionId)
              .withRequestBodyJsonPath("offenderTransactions.size()", 1)
              .withRequestBodyJsonPath("offenderTransactions[0].generalLedgerEntries.size()", 1),
          )
        }

        @Test
        fun `will not attempt to save mapping for the transaction`() {
          mappingApiMockServer.verify(0, postRequestedFor(urlEqualTo("/mapping/transactions")))
        }

        @Test
        fun `will track telemetry for the repair`() {
          verify(telemetryClient).trackEvent(
            eq("transaction-resynchronisation-repair"),
            check {
              assertThat(it["transactionId"]).isEqualTo(transactionId.toString())
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class UnhappyPathNotFound {
      val transactionId = 1234L

      @BeforeEach
      fun setUp() {
        nomisApiMockServer.stubGetPrisonerTransaction(transactionId = transactionId, response = emptyList())
        nomisApiMockServer.stubGetPrisonTransaction(transactionId = transactionId, response = emptyList())
        mappingApiMockServer.stubGetByNomisId(transactionId = transactionId)

        webTestClient.post().uri("/transactions/$transactionId/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNotFound
          .expectBody()
          .jsonPath("userMessage").isEqualTo("Not Found: No transaction found in nomis for transactionId=1234")
      }

      @Test
      fun `will attempt to retrieve prisoner transaction`() {
        nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/transactions/$transactionId")))
      }

      @Test
      fun `will attempt to retrieve prison transaction`() {
        nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/transactions/$transactionId/general-ledger")))
      }

      @Test
      fun `will not send transaction to DPS`() {
        financeApi.verify(0, getRequestedFor(anyUrl()))
      }

      @Test
      fun `will not track telemetry for the repair`() {
        verifyNoInteractions(telemetryClient)
      }
    }
  }
}
