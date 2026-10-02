package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.holds

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceDpsApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceNomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.transactions.toSyncAddHoldRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NotFoundException

@Service
class HoldsSynchronisationService(
  private val nomisApiService: FinanceNomisApiService,
  private val dpsApiService: FinanceDpsApiService,
) {
  suspend fun resynchroniseHold(nomisTransactionId: Long) {
    val transactionEvent = nomisApiService.getPrisonerTransactions(nomisTransactionId).firstOrNull()
      ?: throw NotFoundException("No hold transaction found in nomis for transactionId=$nomisTransactionId")
    dpsApiService.migrateHold(transactionEvent.toSyncAddHoldRequest())
  }
}
