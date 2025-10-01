package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.api.NOMISMigrationApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.api.NOMISSyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncGeneralLedgerTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest

@Service
class FinanceApiService(@Qualifier("financeApiWebClient") webClient: WebClient) {
  private val syncApi = NOMISSyncApi(webClient)
  private val migrateApi = NOMISMigrationApi(webClient)

  suspend fun syncTransactions(request: SyncOffenderTransactionRequest): SyncTransactionReceipt = syncApi
    .prepare(syncApi.postOffenderTransactionRequestConfig(request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncGeneralLedgerTransactions(request: SyncGeneralLedgerTransactionRequest): SyncTransactionReceipt = syncApi
    .prepare(syncApi.postGeneralLedgerTransactionRequestConfig(request))
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun migratePrisonerBalance(prisonNumber: String, migrationDto: PrisonerBalancesSyncRequest) {
    migrateApi
      .prepare(migrateApi.migratePrisonerBalancesRequestConfig(prisonNumber, migrationDto))
      .retrieve()
      .awaitBodilessEntityOrLogAndRethrowBadRequest()
  }

  suspend fun migratePrisonBalance(prisonId: String, migrationDto: GeneralLedgerBalancesSyncRequest) {
    migrateApi
      .prepare(migrateApi.migrateGeneralLedgerBalancesRequestConfig(prisonId, migrationDto))
      .retrieve()
      .awaitBodilessEntityOrLogAndRethrowBadRequest()
  }
}
