package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.TransactionsResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.GeneralLedgerTransactionDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTransactionDto

@Service
class TransactionNomisApiService(@Qualifier("nomisApiWebClient") webClient: WebClient) {
  private val api = TransactionsResourceApi(webClient)

  suspend fun getTransactions(transactionId: Long): List<OffenderTransactionDto> = api
    .prepare(api.getTransactionRequestConfig(transactionId))
    .retrieve()
    .awaitBody()

  suspend fun getGLTransactions(transactionId: Long): List<GeneralLedgerTransactionDto> = api
    .prepare(api.getGLTransactionRequestConfig(transactionId))
    .retrieve()
    .awaitBody()
}
