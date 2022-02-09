package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

@Service
class VisitMappingService(@Qualifier("visitMappingApiWebClient") private val webClient: WebClient) {
  fun findNomisVisitMapping(nomisVisitId: Long): VisitNomisMapping? {
    return webClient.get()
      .uri("/mapping/nomisId/{nomisVisitId}", nomisVisitId)
      .retrieve()
      .bodyToMono(VisitNomisMapping::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .block()
  }

  fun createNomisVisitMapping(nomisVisitId: Long, vsipVisitId: String, migrationId: String) {
    webClient.post()
      .uri("/mapping")
      .bodyValue(
        VisitNomisMapping(
          nomisId = nomisVisitId,
          vsipId = vsipVisitId,
          label = migrationId,
          mappingType = "MIGRATED"
        )
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .block()
  }

  fun findRoomMapping(agencyInternalLocationCode: String, prisonId: String): RoomMapping? {
    return webClient.get()
      .uri("/prison/{prisonId}/room/nomis-room-id/{agencyInternalLocationCode}", prisonId, agencyInternalLocationCode)
      .retrieve()
      .bodyToMono(RoomMapping::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .block()
  }
}

data class VisitNomisMapping(val nomisId: Long, val vsipId: String, val label: String?, val mappingType: String)

data class RoomMapping(val vsipId: String, val isOpen: Boolean)
