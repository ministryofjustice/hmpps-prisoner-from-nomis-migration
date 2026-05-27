package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping

@Service
class VisitMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<VisitNomisMapping>(domainUrl = "/mapping/visits", webClient) {
  suspend fun findNomisVisitMapping(nomisVisitId: Long): VisitNomisMapping? = webClient.get()
    .uri("/mapping/visits/nomisId/{nomisVisitId}", nomisVisitId)
    .retrieve()
    .bodyToMono(VisitNomisMapping::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      Mono.empty()
    }.awaitSingleOrNull()

  suspend fun findRoomMapping(agencyInternalLocationCode: String, prisonId: String): RoomMapping? = webClient.get()
    .uri("/prison/{prisonId}/room/nomis-room-id/{agencyInternalLocationCode}", prisonId, agencyInternalLocationCode)
    .retrieve()
    .bodyToMono(RoomMapping::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      Mono.empty()
    }
    .awaitSingleOrNull()
}

data class VisitNomisMapping(val nomisId: Long, val vsipId: String, val label: String?, val mappingType: String)

data class RoomMapping(val vsipId: String, val isOpen: Boolean)
