package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.LatestMigration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping

@Service
class IncentiveMappingService(@Qualifier("mappingApiWebClient") private val webClient: WebClient) : MigrationMapping {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun findNomisIncentiveMapping(nomisBookingId: Long, nomisIncentiveSequence: Long): IncentiveNomisMapping? =
    webClient.get()
      .uri(
        "/mapping/incentives/nomis-booking-id/{nomisBookingId}/nomis-incentive-sequence/{nomisIncentiveSequence}",
        nomisBookingId,
        nomisIncentiveSequence
      )
      .retrieve()
      .bodyToMono(IncentiveNomisMapping::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }.awaitSingleOrNull()

  suspend fun createNomisIncentiveMigrationMapping(
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

  suspend fun createNomisIncentiveSynchronisationMapping(
    incentiveNomisMapping: IncentiveNomisMapping
  ) {
    createNomisIncentiveMapping(
      nomisBookingId = incentiveNomisMapping.nomisBookingId,
      nomisIncentiveSequence = incentiveNomisMapping.nomisIncentiveSequence,
      incentiveId = incentiveNomisMapping.incentiveId,
      mappingType = incentiveNomisMapping.mappingType
    )
  }

  private suspend fun createNomisIncentiveMapping(
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
      .awaitSingleOrNull()
  }

  override suspend fun findLatestMigration(): LatestMigration? = webClient.get()
    .uri("/mapping/incentives/migrated/latest")
    .retrieve()
    .bodyToMono(LatestMigration::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      Mono.empty()
    }.awaitSingleOrNull()

  override suspend fun getMigrationDetails(migrationId: String): MigrationDetails = webClient.get()
    .uri {
      it.path("/mapping/incentives/migration-id/{migrationId}")
        .queryParam("size", 1)
        .build(migrationId)
    }
    .retrieve()
    .bodyToMono(MigrationDetails::class.java).awaitSingle()

  override suspend fun getMigrationCount(migrationId: String): Long = webClient.get()
    .uri {
      it.path("/mapping/incentives/migration-id/{migrationId}")
        .queryParam("size", 1)
        .build(migrationId)
    }
    .retrieve()
    .bodyToMono(MigrationDetails::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      Mono.empty()
    }.awaitSingleOrNull()?.count ?: 0

  suspend fun deleteIncentiveMapping(incentiveId: Long): Unit? = webClient.delete()
    .uri(
      "/mapping/incentives/incentive-id/{incentiveId}",
      incentiveId,
    )
    .retrieve()
    .bodyToMono(Unit::class.java)
    .onErrorResume(WebClientResponseException::class.java) {
      log.error("Unable to deleting mapping for incentiveId $incentiveId but ignoring and allowing dangling record", it)
      Mono.empty()
    }.awaitSingleOrNull()
}

data class IncentiveNomisMapping(
  val nomisBookingId: Long,
  val nomisIncentiveSequence: Long,
  val incentiveId: Long,
  val label: String? = null,
  val mappingType: String
)
