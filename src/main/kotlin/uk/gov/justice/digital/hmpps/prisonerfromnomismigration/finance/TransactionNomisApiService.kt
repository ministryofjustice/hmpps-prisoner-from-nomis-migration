package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.GeneralLedgerTransactionDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTransactionDto

@Service
class TransactionNomisApiService(@Qualifier("nomisApiWebClient") private val webClient: WebClient) {
  suspend fun getTransactions(transactionId: Long): List<OffenderTransactionDto> = webClient.get()
    .uri("/transactions/{transactionId}", transactionId)
    .retrieve()
    .awaitBody()

  suspend fun getGLTransactions(transactionId: Long): List<GeneralLedgerTransactionDto> = webClient.get()
    .uri("/transactions/{transactionId}/general-ledger", transactionId)
    .retrieve()
    .awaitBody()
}
