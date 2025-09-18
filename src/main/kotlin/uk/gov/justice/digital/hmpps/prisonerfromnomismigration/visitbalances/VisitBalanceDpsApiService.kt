package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.api.NomisControllerApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerMigrationDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerSyncDto

@Service
class VisitBalanceDpsApiService(@Qualifier("visitBalanceApiWebClient") webClient: WebClient) {
  private val api = NomisControllerApi(webClient)

  suspend fun migrateVisitBalance(visitBalanceMigrateDto: VisitAllocationPrisonerMigrationDto) {
    api.prepare(api.migratePrisonerVisitOrdersRequestConfig(visitBalanceMigrateDto))
      .retrieve()
      .awaitBodilessEntityOrLogAndRethrowBadRequest()
  }

  suspend fun syncVisitBalanceAdjustment(visitBalanceSyncDto: VisitAllocationPrisonerSyncDto) {
    api.prepare(api.syncPrisonerVisitOrdersRequestConfig(visitBalanceSyncDto))
      .retrieve()
      .awaitBodilessEntity()
  }
}
