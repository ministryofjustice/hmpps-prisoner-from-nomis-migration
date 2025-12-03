package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceDetailResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerMigrationDto
import java.time.LocalDate

fun VisitBalanceDetailResponse.toMigrationDto() = VisitAllocationPrisonerMigrationDto(
  prisonerId = prisonNumber,
  voBalance = remainingVisitOrders,
  pvoBalance = remainingPrivilegedVisitOrders,
  lastVoAllocationDate = lastIEPAllocationDate ?: LocalDate.now().minusDays(14),
)
