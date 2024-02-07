package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IncidentMappingDto

@Service
class IncidentsMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<IncidentMappingDto>(domainUrl = "/mapping/incidents", webClient) {

  suspend fun findNomisIncidentMapping(nomisIncidentId: Long): IncidentMappingDto? =
    webClient.get()
      .uri("/mapping/incidents/nomis-incident-id/{nomisIncidentId}", nomisIncidentId)
      .retrieve()
      .bodyToMono(IncidentMappingDto::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .awaitSingleOrNull()

  suspend fun deleteIncidentMapping(
    incidentId: String,
  ): Unit =
    webClient.delete()
      .uri("/mapping/incidents/incident-id/{incidentId}", incidentId)
      .retrieve()
      .awaitBody()
}
