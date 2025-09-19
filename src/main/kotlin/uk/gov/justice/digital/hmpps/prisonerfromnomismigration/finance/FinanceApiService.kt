package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.api.NOMISSyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest

@Service
class FinanceApiService(@Qualifier("financeApiWebClient") webClient: WebClient) {
  private val api = NOMISSyncApi(webClient)

  suspend fun syncTransactions(request: SyncOffenderTransactionRequest): SyncTransactionReceipt = api
    .prepare(api.postOffenderTransactionRequestConfig(request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncGeneralLedgerTransactions(request: SyncGeneralLedgerTransactionRequest): SyncTransactionReceipt = api
    .prepare(api.postGeneralLedgerTransactionRequestConfig(request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
}
