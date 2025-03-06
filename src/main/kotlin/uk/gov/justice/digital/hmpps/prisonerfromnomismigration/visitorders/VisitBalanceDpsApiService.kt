package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitorders

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodilessEntityOrLogAndRethrowBadRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerMigrationDto

@Service
class VisitBalanceDpsApiService(@Qualifier("visitBalanceApiWebClient") private val webClient: WebClient) {
  suspend fun migrateVisitBalance(visitBalanceMigrateDto: VisitAllocationPrisonerMigrationDto) {
    webClient.post()
      .uri("/visits/allocation/prisoner/migrate")
      .bodyValue(visitBalanceMigrateDto)
      .retrieve()
      .awaitBodilessEntityOrLogAndRethrowBadRequest()
  }
}
