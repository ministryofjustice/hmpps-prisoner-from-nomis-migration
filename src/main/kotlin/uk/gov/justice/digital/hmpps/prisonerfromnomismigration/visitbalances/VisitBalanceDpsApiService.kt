package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerMigrationDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerSyncBookingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerSyncDto

@Service
class VisitBalanceDpsApiService(@Qualifier("visitBalanceApiWebClient") private val webClient: WebClient) {
  suspend fun migrateVisitBalance(visitBalanceMigrateDto: VisitAllocationPrisonerMigrationDto) {
    webClient.post()
      .uri("/visits/allocation/prisoner/migrate")
      .bodyValue(visitBalanceMigrateDto)
      .retrieve()
      .awaitBodilessEntityOrLogAndRethrowBadRequest()
  }

  suspend fun syncVisitBalances(visitBalancesSyncDto: VisitAllocationPrisonerSyncBookingDto) {
    webClient.post()
      .uri("/visits/allocation/prisoner/sync/booking")
      .bodyValue(visitBalancesSyncDto)
      .retrieve()
      .awaitBodilessEntityOrLogAndRethrowBadRequest()
  }

  suspend fun syncVisitBalanceAdjustment(visitBalanceSyncDto: VisitAllocationPrisonerSyncDto) {
    webClient.post()
      .uri("/visits/allocation/prisoner/sync")
      .bodyValue(visitBalanceSyncDto)
      .retrieve()
      .awaitBodilessEntity()
  }
}
