package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.transactions

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
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension.Companion.financeApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances.AdvancesMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances.AdvancesNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances.prisonerAdvance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.nomisGLTransactions
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.util.UUID

@TestPropertySource(properties = ["offender_transactions.process.advances=true"])
class PrisonTransactionDataRepairResourceIntTest(
  @Autowired private val nomisApiMockServer: FinanceNomisApiMockServer,
  @Autowired private val mappingApiMockServer: FinanceMappingApiMockServer,
  @Autowired private val advancesNomisApiMockServer: AdvancesNomisApiMockServer,
  @Autowired private val advancesMappingApiMockServer: AdvancesMappingApiMockServer,
) : FinanceIntegrationTestBase() {

  @DisplayName("POST /prison-transactions/{transactionId}/repair")
  @Nested
  inner class PrisonTransactionRepair {
    val transactionId = 1234

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/prison-transactions/$transactionId/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/prison-transactions/$transactionId/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/prison-transactions/$transactionId/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @Nested
      inner class HappyPathPrisonTransaction {
        val transactionId = 1234L
        val dpsAdvanceId = UUID.randomUUID()
        val deductionId = 65432L
        val advance = prisonerAdvance(prisonNumber = "A3864DZ")
        val writeOffTransaction = nomisGLTransactions(transactionId = transactionId).first().copy(
          type = "WRT",
          offenderDeductionId = deductionId,
        )

        @BeforeEach
        fun setUp() {
          nomisApiMockServer.stubGetPrisonerTransaction(transactionId = transactionId, response = emptyList())
          nomisApiMockServer.stubGetPrisonTransaction(transactionId = transactionId, response = listOf(writeOffTransaction))
          mappingApiMockServer.stubGetByNomisId(transactionId = transactionId, mapping = null)
          advancesNomisApiMockServer.stubGetAdvanceByDeductionId(deductionId, advance)
          advancesMappingApiMockServer.stubGetAdvanceByNomisIdOrNull(
            nomisAdvanceId = advance.id,
            dpsId = dpsAdvanceId.toString(),
          )
          financeApi.stubWriteOffAdvance(advanceUuid = dpsAdvanceId)

          webTestClient.get().uri("/prison-transactions/$transactionId/repair")
            .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
            .exchange()
            .expectStatus().isOk
        }

        @Test
        fun `will retrieve the prison transaction`() {
          nomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/transactions/$transactionId/general-ledger")))
        }

        @Test
        fun `will retrieve the transaction mapping`() {
          mappingApiMockServer.verify(
            getRequestedFor(urlPathEqualTo("/mapping/transactions/nomis-transaction-id/$transactionId")),
          )
        }

        @Test
        fun `will look up the advance by deduction id and write it off in DPS`() {
          advancesNomisApiMockServer.verify(
            1,
            getRequestedFor(urlPathEqualTo("/finance/advances/deduction-id/$deductionId")),
          )
          advancesMappingApiMockServer.verify(
            1,
            getRequestedFor(urlPathEqualTo("/mapping/advances/nomis-id/${advance.id}")),
          )
          financeApi.verify(
            1,
            postRequestedFor(urlPathEqualTo("/sync/advances/$dpsAdvanceId/write-off"))
              .withRequestBodyJsonPath("writeOffDateTime", writeOffTransaction.transactionTimestamp)
              .withRequestBodyJsonPath("writtenOffBy", writeOffTransaction.lastModifiedBy ?: writeOffTransaction.createdBy)
              .withRequestBodyJsonPath("writtenOffAmount", writeOffTransaction.amount),
          )
        }

        @Test
        fun `will not create an offender transaction or transaction mapping`() {
          financeApi.verify(0, postRequestedFor(urlPathEqualTo("/sync/offender-transactions")))
          mappingApiMockServer.verify(0, postRequestedFor(urlPathEqualTo("/mapping/transactions")))
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
        nomisApiMockServer.stubGetPrisonTransaction(transactionId = transactionId, response = emptyList())

        webTestClient.get().uri("/prison-transactions/$transactionId/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNotFound
          .expectBody()
          .jsonPath("userMessage").isEqualTo("Not Found: No prison transaction found in nomis for transactionId=1234")
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
