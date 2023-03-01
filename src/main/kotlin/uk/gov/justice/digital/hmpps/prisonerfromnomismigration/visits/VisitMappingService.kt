package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.LatestMigration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationDetails

@Service
class VisitMappingService(@Qualifier("mappingApiWebClient") private val webClient: WebClient) {
  suspend fun findNomisVisitMapping(nomisVisitId: Long): VisitNomisMapping? = webClient.get()
    .uri("/mapping/visits/nomisId/{nomisVisitId}", nomisVisitId)
    .retrieve()
    .bodyToMono(VisitNomisMapping::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      Mono.empty()
    }.awaitSingleOrNull()

  suspend fun createNomisVisitMapping(nomisVisitId: Long, vsipVisitId: String, migrationId: String) {
    webClient.post()
      .uri("/mapping/visits")
      .bodyValue(
        VisitNomisMapping(
          nomisId = nomisVisitId,
          vsipId = vsipVisitId,
          label = migrationId,
          mappingType = "MIGRATED",
        ),
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .awaitSingleOrNull()
  }

  suspend fun findRoomMapping(agencyInternalLocationCode: String, prisonId: String): RoomMapping? {
    return webClient.get()
      .uri("/prison/{prisonId}/room/nomis-room-id/{agencyInternalLocationCode}", prisonId, agencyInternalLocationCode)
      .retrieve()
      .bodyToMono(RoomMapping::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .awaitSingleOrNull()
  }

  suspend fun findLatestMigration(): LatestMigration? = webClient.get()
    .uri("/mapping/visits/migrated/latest")
    .retrieve()
    .bodyToMono(LatestMigration::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      Mono.empty()
    }
    .awaitSingleOrNull()

  suspend fun getMigrationDetails(migrationId: String): MigrationDetails = webClient.get()
    .uri {
      it.path("/mapping/visits/migration-id/{migrationId}")
        .queryParam("size", 1)
        .build(migrationId)
    }
    .retrieve()
    .bodyToMono(MigrationDetails::class.java)
    .awaitSingle()!!

  suspend fun getMigrationCount(migrationId: String): Long = webClient.get()
    .uri {
      it.path("/mapping/visits/migration-id/{migrationId}")
        .queryParam("size", 1)
        .build(migrationId)
    }
    .retrieve()
    .bodyToMono(MigrationDetails::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      Mono.empty()
    }
    .awaitSingleOrNull()?.count ?: 0
}

data class VisitNomisMapping(val nomisId: Long, val vsipId: String, val label: String?, val mappingType: String)

data class RoomMapping(val vsipId: String, val isOpen: Boolean)
