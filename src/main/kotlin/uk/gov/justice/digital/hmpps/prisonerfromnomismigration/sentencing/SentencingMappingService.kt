package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

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
class SentencingMappingService(@Qualifier("mappingApiWebClient") private val webClient: WebClient) {
  suspend fun findNomisSentencingAdjustmentMapping(
    nomisAdjustmentId: Long,
    nomisAdjustmentType: String,
  ): SentencingAdjustmentNomisMapping? {
    return webClient.get()
      .uri(
        "/mapping/sentencing/adjustments/nomis-adjustment-type/{nomisAdjustmentType}/nomis-adjustment-id/{nomisAdjustmentId}",
        nomisAdjustmentType,
        nomisAdjustmentId,
      )
      .retrieve()
      .bodyToMono(SentencingAdjustmentNomisMapping::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .awaitSingleOrNull()
  }

  suspend fun createNomisSentencingAdjustmentMigrationMapping(
    nomisAdjustmentId: Long,
    nomisAdjustmentType: String,
    sentenceAdjustmentId: Long,
    migrationId: String
  ) {
    createNomisSentenceAdjustmentMapping(
      nomisAdjustmentId = nomisAdjustmentId,
      nomisAdjustmentType = nomisAdjustmentType,
      sentenceAdjustmentId = sentenceAdjustmentId,
      migrationId = migrationId,
      mappingType = "MIGRATED"
    )
  }

  private suspend fun createNomisSentenceAdjustmentMapping(
    nomisAdjustmentId: Long,
    nomisAdjustmentType: String,
    sentenceAdjustmentId: Long,
    mappingType: String,
    migrationId: String? = null
  ) {
    webClient.post()
      .uri("/mapping/sentencing/adjustments")
      .bodyValue(
        SentencingAdjustmentNomisMapping(
          nomisAdjustmentId = nomisAdjustmentId,
          nomisAdjustmentType = nomisAdjustmentType,
          sentenceAdjustmentId = sentenceAdjustmentId,
          label = migrationId,
          mappingType = mappingType
        )
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .awaitSingleOrNull()
  }

  suspend fun findLatestMigration(): LatestMigration? = webClient.get()
    .uri("/mapping/sentencing/adjustments/migrated/latest")
    .retrieve()
    .bodyToMono(LatestMigration::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      Mono.empty()
    }
    .awaitSingleOrNull()

  suspend fun getMigrationDetails(migrationId: String): MigrationDetails = webClient.get()
    .uri {
      it.path("/mapping/sentencing/adjustments/migration-id/{migrationId}")
        .queryParam("size", 1)
        .build(migrationId)
    }
    .retrieve()
    .bodyToMono(MigrationDetails::class.java)
    .awaitSingle()!!

  suspend fun getMigrationCount(migrationId: String): Long = webClient.get()
    .uri {
      it.path("/mapping/sentencing/adjustments/migration-id/{migrationId}")
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

data class SentencingAdjustmentNomisMapping(
  val nomisAdjustmentId: Long,
  val nomisAdjustmentType: String,
  val sentenceAdjustmentId: Long,
  val label: String? = null,
  val mappingType: String
)
