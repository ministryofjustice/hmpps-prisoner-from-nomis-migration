package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNotFound

@Service
class IncidentsService(@Qualifier("incidentsApiWebClient") private val webClient: WebClient) {
  suspend fun migrateIncident(migrateRequest: IncidentMigrateRequest): Incident =
    webClient.post()
      .uri("/incidents/migrate")
      .bodyValue(migrateRequest)
      .retrieve()
      .awaitBody()

  suspend fun syncIncident(syncRequest: IncidentSyncRequest): Incident =
    webClient.put()
      .uri("/incidents/sync")
      .bodyValue(syncRequest)
      .retrieve()
      .awaitBody()

  suspend fun deleteIncident(incidentId: String) =
    webClient.delete()
      .uri("/incidents/sync/{incidentId}", incidentId)
      .retrieve()
      .awaitBodyOrNotFound<Unit>()
}
