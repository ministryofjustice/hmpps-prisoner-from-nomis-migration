package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.LatestMigration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationDetails

@Service
class IncentiveMappingService(@Qualifier("mappingApiWebClient") private val webClient: WebClient) {
  fun findNomisIncentiveMapping(nomisBookingId: Long, nomisIncentiveSequence: Long): IncentiveNomisMapping? {
    return webClient.get()
      .uri(
        "/mapping/incentives/nomis-booking-id/{nomisBookingId}/nomis-incentive-sequence/{nomisIncentiveSequence}",
        nomisBookingId,
        nomisIncentiveSequence
      )
      .retrieve()
      .bodyToMono(IncentiveNomisMapping::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .block()
  }

  fun createNomisIncentiveMapping(nomisBookingId: Long, nomisSequence: Long, incentiveId: Long, migrationId: String) {
    webClient.post()
      .uri("/mapping/incentives")
      .bodyValue(
        IncentiveNomisMapping(
          nomisBookingId = nomisBookingId,
          nomisSequence = nomisSequence,
          incentiveId = incentiveId,
          label = migrationId,
          mappingType = "MIGRATED"
        )
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .block()
  }

  fun findLatestMigration(): LatestMigration? = webClient.get()
    .uri("/mapping/incentives/migrated/latest")
    .retrieve()
    .bodyToMono(LatestMigration::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      Mono.empty()
    }
    .block()

  fun getMigrationDetails(migrationId: String): MigrationDetails = webClient.get()
    .uri {
      it.path("/mapping/incentives/migration-id/{migrationId}")
        .queryParam("size", 1)
        .build(migrationId)
    }
    .retrieve()
    .bodyToMono(MigrationDetails::class.java)
    .block()!!

  fun getMigrationCount(migrationId: String): Long = webClient.get()
    .uri {
      it.path("/mapping/incentives/migration-id/{migrationId}")
        .queryParam("size", 1)
        .build(migrationId)
    }
    .retrieve()
    .bodyToMono(MigrationDetails::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      Mono.empty()
    }
    .block()?.count ?: 0
}

data class IncentiveNomisMapping(
  val nomisBookingId: Long,
  val nomisSequence: Long,
  val incentiveId: Long,
  val label: String?,
  val mappingType: String
)
