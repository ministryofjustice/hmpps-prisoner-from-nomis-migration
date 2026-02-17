package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import org.springframework.stereotype.Service

@Service
class PrisonBalanceSynchronisationService(
  private val nomisApiService: FinanceNomisApiService,
  private val dpsApiService: FinanceApiService,
) {
  suspend fun resynchronisePrisonBalance(prisonId: String) {
    val prisonBalance = nomisApiService.getPrisonBalance(prisonId)
    dpsApiService.migratePrisonBalance(prisonBalance.prisonId, prisonBalance.toMigrationDto())
  }
}
