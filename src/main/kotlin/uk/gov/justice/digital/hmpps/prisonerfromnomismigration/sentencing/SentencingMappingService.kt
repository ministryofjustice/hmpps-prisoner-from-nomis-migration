package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.LatestMigration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationDetails

@Service
class SentencingMappingService(@Qualifier("mappingApiWebClient") private val webClient: WebClient) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun findNomisSentenceAdjustmentMapping(nomisSentenceAdjustmentId: Long): SentenceAdjustmentNomisMapping? {
    return webClient.get()
      .uri(
        "/mapping/sentence-adjustments/nomis-sentencing-adjustment-id/{nomisSentenceAdjustmentId}",
        nomisSentenceAdjustmentId
      )
      .retrieve()
      .bodyToMono(SentenceAdjustmentNomisMapping::class.java)
      .onErrorResume(WebClientResponseException.NotFound::class.java) {
        Mono.empty()
      }
      .block()
  }

  fun createNomisSentenceAdjustmentMigrationMapping(
    nomisSentenceAdjustmentId: Long,
    sentenceAdjustmentId: Long,
    migrationId: String
  ) {
    createNomisSentenceAdjustmentMapping(
      nomisSentenceAdjustmentId = nomisSentenceAdjustmentId,
      sentenceAdjustmentId = sentenceAdjustmentId,
      migrationId = migrationId,
      mappingType = "MIGRATED"
    )
  }

  private fun createNomisSentenceAdjustmentMapping(
    nomisSentenceAdjustmentId: Long,
    sentenceAdjustmentId: Long,
    mappingType: String,
    migrationId: String? = null
  ) {
    webClient.post()
      .uri("/mapping/sentence-adjustments")
      .bodyValue(
        SentenceAdjustmentNomisMapping(
          nomisSentenceAdjustmentId = nomisSentenceAdjustmentId,
          sentenceAdjustmentId = sentenceAdjustmentId,
          label = migrationId,
          mappingType = mappingType
        )
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .block()
  }

  fun findLatestMigration(): LatestMigration? = webClient.get()
    .uri("/mapping/sentence-adjustments/migrated/latest")
    .retrieve()
    .bodyToMono(LatestMigration::class.java)
    .onErrorResume(WebClientResponseException.NotFound::class.java) {
      Mono.empty()
    }
    .block()

  fun getMigrationDetails(migrationId: String): MigrationDetails = webClient.get()
    .uri {
      it.path("/mapping/sentence-adjustments/migration-id/{migrationId}")
        .queryParam("size", 1)
        .build(migrationId)
    }
    .retrieve()
    .bodyToMono(MigrationDetails::class.java)
    .block()!!

  fun getMigrationCount(migrationId: String): Long = webClient.get()
    .uri {
      it.path("/mapping/sentence-adjustments/migration-id/{migrationId}")
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

data class SentenceAdjustmentNomisMapping(
  val nomisSentenceAdjustmentId: Long,
  val sentenceAdjustmentId: Long,
  val label: String? = null,
  val mappingType: String
)
