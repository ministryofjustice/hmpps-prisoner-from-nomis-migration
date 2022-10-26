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

  fun createNomisIncentiveMigrationMapping(
    nomisBookingId: Long,
    nomisIncentiveSequence: Long,
    incentiveId: Long,
    migrationId: String
  ) {
    createNomisIncentiveMapping(
      nomisBookingId = nomisBookingId,
      nomisIncentiveSequence = nomisIncentiveSequence,
      incentiveId = incentiveId,
      migrationId = migrationId,
      mappingType = "MIGRATED"
    )
  }

  fun createNomisIncentiveSynchronisationMapping(
    nomisBookingId: Long,
    nomisIncentiveSequence: Long,
    incentiveId: Long,
  ) {
    createNomisIncentiveMapping(
      nomisBookingId = nomisBookingId,
      nomisIncentiveSequence = nomisIncentiveSequence,
      incentiveId = incentiveId,
      mappingType = "NOMIS_CREATED"
    )
  }

  private fun createNomisIncentiveMapping(
    nomisBookingId: Long,
    nomisIncentiveSequence: Long,
    incentiveId: Long,
    mappingType: String,
    migrationId: String? = null
  ) {
    webClient.post()
      .uri("/mapping/incentives")
      .bodyValue(
        IncentiveNomisMapping(
          nomisBookingId = nomisBookingId,
          nomisIncentiveSequence = nomisIncentiveSequence,
          incentiveId = incentiveId,
          label = migrationId,
          mappingType = mappingType
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

  fun deleteIncentiveMapping(nomisBookingId: Long, nomisIncentiveSequence: Long, incentiveId: Long) {
    // TODO - delete mapping for completeness though if we don't it just leaves a dangling mapping
  }
}

data class IncentiveNomisMapping(
  val nomisBookingId: Long,
  val nomisIncentiveSequence: Long,
  val incentiveId: Long,
  val label: String? = null,
  val mappingType: String
)
