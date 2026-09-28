package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.api.AdvancesApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.api.HoldsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.api.NOMISSyncApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.GeneralLedgerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.HoldResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.PrisonerBalancesSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncCreateAdvanceRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncCreateHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncOffenderTransactionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncReleaseHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncReleasedHoldResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.model.SyncTransactionReceipt
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitOrLogAndRethrowBadRequest
import java.util.UUID

@Service
class FinanceDpsApiService(@Qualifier("financeApiWebClient") private val webClient: WebClient) {
  private val syncApi = NOMISSyncApi(webClient)
  private val holdsApi = HoldsApi(webClient)
  private val advancesApi = AdvancesApi(webClient)

  suspend fun syncPrisonerTransactions(request: SyncOffenderTransactionRequest): SyncTransactionReceipt = syncApi.postOffenderTransaction(request)
    .awaitOrLogAndRethrowBadRequest()

  suspend fun migratePrisonerBalance(prisonNumber: String, migrationDto: PrisonerBalancesSyncRequest) {
    syncApi.migratePrisonerBalances(prisonNumber, migrationDto)
      .awaitOrLogAndRethrowBadRequest()
  }

  suspend fun migratePrisonBalance(prisonId: String, migrationDto: GeneralLedgerBalancesSyncRequest) {
    syncApi.migrateGeneralLedgerBalances(prisonId, migrationDto)
      .awaitOrLogAndRethrowBadRequest()
  }

  suspend fun syncAddHoldTransaction(request: SyncCreateHoldRequest): HoldResponse = holdsApi.postHolds(request)
    .awaitOrLogAndRethrowBadRequest()

  suspend fun syncReleaseHoldTransaction(holdNumber: Long, request: SyncReleaseHoldRequest): SyncReleasedHoldResponse = holdsApi.releaseHold(holdNumber, request)
    .awaitOrLogAndRethrowBadRequest()

  suspend fun syncPrisonerAdvance(request: SyncCreateAdvanceRecordRequest): SyncCreateAdvanceResponse = webClient.post()
    .uri("/sync/advances")
    .bodyValue(request)
    .retrieve()
    .awaitBodyOrLogAndRethrowBadRequest()
  // TODO add correct return type for syncPrisonerAdvance when the API is updated
  // advancesApi.postAdvance(request)
  //  .awaitOrLogAndRethrowBadRequest()
}

class SyncCreateAdvanceResponse(val id: UUID)
