package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.prisonbalances

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceDpsApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceNomisApiService

@Service
class PrisonBalanceSynchronisationService(
  private val nomisApiService: FinanceNomisApiService,
  private val dpsApiService: FinanceDpsApiService,
) {
  suspend fun resynchronisePrisonBalance(prisonId: String) {
    val prisonBalance = nomisApiService.getPrisonBalance(prisonId)
    dpsApiService.migratePrisonBalance(prisonBalance.prisonId, prisonBalance.toMigrationDto())
  }
}
