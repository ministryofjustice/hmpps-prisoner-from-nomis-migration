package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerMigrationDto
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

  suspend fun syncVisitBalances(visitAllocationResyncDto: VisitAllocationPrisonerSyncBookingDto) {
    webClient.post()
      .uri("/visits/allocation/prisoner/sync/booking")
      .bodyValue(visitAllocationResyncDto)
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

// TODO remove when endpoint set up
data class VisitAllocationPrisonerSyncBookingDto(
  val firstPrisonerId: String,
  val firstPrisonerVoBalance: Int,
  val firstPrisonerPvoBalance: Int,
  val secondPrisonerId: String,
  val secondPrisonerVoBalance: Int,
  val secondPrisonerPvoBalance: Int,
)
