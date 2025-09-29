package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import org.springframework.stereotype.Service

@Service
class PrisonerBalanceSynchronisationService(
  private val nomisApiService: PrisonerBalanceNomisApiService,
  private val dpsApiService: FinanceApiService,
) {
  suspend fun resynchronisePrisonerBalance(nomisRootOffenderId: Long) {
    val prisonerBalance = nomisApiService.getPrisonerBalance(nomisRootOffenderId)
    dpsApiService.migratePrisonerBalance(prisonerBalance.prisonNumber, prisonerBalance.toMigrationDto())
  }
}
