package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.transactions

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceNomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.nomisGLTransactions
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(NomisApiExtension::class)
@SpringAPIServiceTest
@Import(FinanceNomisApiService::class, FinanceNomisApiMockServer::class)
class TransactionsNomisApiServiceTest {
  private companion object {
    const val TRANSACTION_ID = 5678L
  }

  @Autowired
  private lateinit var apiService: FinanceNomisApiService

  @Autowired
  private lateinit var mockServer: FinanceNomisApiMockServer

  @Nested
  @DisplayName("GET /transactions/{transactionId}")
  inner class GetPrisonerTransactions {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetPrisonerTransaction(transactionId = TRANSACTION_ID)

      apiService.getPrisonerTransactions(TRANSACTION_ID)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS transaction id to service`() = runTest {
      mockServer.stubGetPrisonerTransaction(transactionId = TRANSACTION_ID)

      apiService.getPrisonerTransactions(TRANSACTION_ID)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/transactions/$TRANSACTION_ID")),
      )
    }

    @Test
    fun `will return prisoner transaction details`() = runTest {
      mockServer.stubGetPrisonerTransaction(transactionId = TRANSACTION_ID)

      val transactions = apiService.getPrisonerTransactions(TRANSACTION_ID)

      assertThat(transactions).hasSize(1)
      with(transactions.first()) {
        assertThat(transactionId).isEqualTo(TRANSACTION_ID)
        assertThat(offenderId).isEqualTo(9876)
        assertThat(offenderNo).isEqualTo("A3864DZ")
        assertThat(bookingId).isEqualTo(123456)
        assertThat(amount).isEqualTo(BigDecimal.valueOf(5.42))
        assertThat(type).isEqualTo("type")
        assertThat(entryDate).isEqualTo(LocalDate.parse("2021-02-03"))
        assertThat(createdAt).isEqualTo(LocalDateTime.parse("2021-02-03T04:05:06"))
        assertThat(generalLedgerTransactions).hasSize(1)
      }
    }
  }

  @Nested
  @DisplayName("GET /transactions/{transactionId}/general-ledger")
  inner class GetPrisonTransactions {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetPrisonTransaction(transactionId = TRANSACTION_ID)

      apiService.getPrisonTransactions(TRANSACTION_ID)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS transaction id to service`() = runTest {
      mockServer.stubGetPrisonTransaction(transactionId = TRANSACTION_ID)

      apiService.getPrisonTransactions(TRANSACTION_ID)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/transactions/$TRANSACTION_ID/general-ledger")),
      )
    }

    @Test
    fun `will return prison transaction details`() = runTest {
      mockServer.stubGetPrisonTransaction(transactionId = TRANSACTION_ID)

      val transactions = apiService.getPrisonTransactions(TRANSACTION_ID)

      assertThat(transactions).containsExactlyElementsOf(nomisGLTransactions(TRANSACTION_ID))
      with(transactions.first()) {
        assertThat(transactionId).isEqualTo(TRANSACTION_ID)
        assertThat(transactionEntrySequence).isEqualTo(1)
        assertThat(generalLedgerEntrySequence).isEqualTo(1)
        assertThat(amount).isEqualTo(BigDecimal.valueOf(6.71))
        assertThat(accountCode).isEqualTo(1021)
        assertThat(transactionTimestamp).isEqualTo(LocalDateTime.parse("2021-02-03T04:05:09"))
      }
    }
  }
}
