package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Service
class IncidentsService(@Qualifier("incidentsApiWebClient") private val webClient: WebClient) {
  suspend fun migrateIncident(migrateRequest: IncidentMigrateRequest): IncidentMigrateResponse =
    webClient.post()
      .uri("/migrate")
      .bodyValue(migrateRequest)
      .retrieve()
      .awaitBody()
}
