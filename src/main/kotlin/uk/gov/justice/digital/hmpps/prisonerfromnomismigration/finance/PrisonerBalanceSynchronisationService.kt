package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NotFoundException

@Service
class PrisonerBalanceSynchronisationService(
  private val prisonerBalanceNomisApiService: PrisonerBalanceNomisApiService,
  private val nomisApiService: NomisApiService,
  private val dpsApiService: FinanceApiService,
) {
  suspend fun resynchronisePrisonerBalance(nomisRootOffenderId: Long) {
    val prisonerBalance = prisonerBalanceNomisApiService.getPrisonerBalance(nomisRootOffenderId)
    dpsApiService.migratePrisonerBalance(prisonerBalance.prisonNumber, prisonerBalance.toMigrationDto())
  }

  suspend fun resynchronisePrisonerBalance(offenderNo: String) {
    nomisApiService.getPrisonerDetails(offenderNo)
      ?.run { resynchronisePrisonerBalance(rootOffenderId!!) }
      // rootOffenderId is nullable but there are no nulls in the table in prod
      ?: throw NotFoundException("offenderNo $offenderNo not found")
  }
}
