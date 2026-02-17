package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.PrisonBalanceResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.PrisonTransactionsResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.api.PrisonerTransactionsResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.GeneralLedgerTransactionDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderTransactionDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonBalanceDto

@Service
class FinanceNomisApiService(@Qualifier("nomisApiWebClient") webClient: WebClient) {
  private val prisonerTransactionsApi = PrisonerTransactionsResourceApi(webClient)
  private val prisonTransactionsApi = PrisonTransactionsResourceApi(webClient)
  private val prisonApi = PrisonBalanceResourceApi(webClient)

  suspend fun getPrisonerTransactions(transactionId: Long): List<OffenderTransactionDto> = prisonerTransactionsApi
    .getTransaction(transactionId)
    .awaitSingle()

  suspend fun getPrisonTransactions(transactionId: Long): List<GeneralLedgerTransactionDto> = prisonTransactionsApi
    .getGLTransaction(transactionId)
    .awaitSingle()

  suspend fun getPrisonBalanceIds(): List<String> = prisonApi
    .getPrisonIds()
    .awaitSingle()

  suspend fun getPrisonBalance(prisonId: String): PrisonBalanceDto = prisonApi
    .getPrisonBalance(prisonId)
    .awaitSingle()
}
