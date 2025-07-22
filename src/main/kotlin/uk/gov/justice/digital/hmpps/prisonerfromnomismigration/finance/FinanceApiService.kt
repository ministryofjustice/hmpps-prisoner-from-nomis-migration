package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest

@Service
class FinanceApiService(@Qualifier("financeApiWebClient") private val webClient: WebClient) {
  suspend fun syncTransactions(request: SyncOffenderTransactionRequest): SyncTransactionReceipt = webClient
    .post()
    .uri("/sync/offender-transactions")
    .bodyValue(request)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncGeneralLedgerTransactions(request: SyncGeneralLedgerTransactionRequest): SyncTransactionReceipt = webClient
    .post()
    .uri("/sync/general-ledger-transactions")
    .bodyValue(request)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
