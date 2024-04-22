package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto

@Service
class LocationsMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) :
  MigrationMapping<LocationMappingDto>(domainUrl = "/mapping/locations", webClient) {

  suspend fun createMapping(request: LocationMappingDto) {
    webClient.post()
      .uri("/mapping/locations")
      .bodyValue(request)
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun getMappingGivenNomisId(id: Long): LocationMappingDto? =
    webClient.get()
      .uri("/mapping/locations/nomis/{id}", id)
      .retrieve()
      .bodyToMono(LocationMappingDto::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .awaitSingleOrNull()

  suspend fun deleteMappingGivenDpsId(id: String) {
    webClient.delete()
      .uri("/mapping/locations/dps/{id}", id)
      .retrieve()
      .awaitBodilessEntity()
  }
}
