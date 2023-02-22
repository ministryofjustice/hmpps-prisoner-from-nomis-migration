package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import kotlinx.coroutines.reactive.awaitFirstOrDefault
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.LatestMigration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationDetails

@Service
class SentencingMappingService(@Qualifier("mappingApiWebClient") private val webClient: WebClient) {
  suspend fun findNomisSentencingAdjustmentMapping(
    nomisAdjustmentId: Long,
    nomisAdjustmentCategory: String,
  ): SentencingAdjustmentNomisMapping? {
    return webClient.get()
      .uri(
        "/mapping/sentencing/adjustments/nomis-adjustment-category/{nomisAdjustmentCategory}/nomis-adjustment-id/{nomisAdjustmentId}",
        nomisAdjustmentCategory,
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
    nomisAdjustmentCategory: String,
    adjustmentId: String,
    migrationId: String
  ) =
    createNomisSentenceAdjustmentMapping(
      nomisAdjustmentId = nomisAdjustmentId,
      nomisAdjustmentCategory = nomisAdjustmentCategory,
      adjustmentId = adjustmentId,
      migrationId = migrationId,
      mappingType = "MIGRATED"
    )

  suspend fun createNomisSentencingAdjustmentSynchronisationMapping(
    nomisAdjustmentId: Long,
    nomisAdjustmentCategory: String,
    adjustmentId: String,
  ) =
    createNomisSentenceAdjustmentMapping(
      nomisAdjustmentId = nomisAdjustmentId,
      nomisAdjustmentCategory = nomisAdjustmentCategory,
      adjustmentId = adjustmentId,
      mappingType = "NOMIS_CREATED"
    )

  data class CreateMappingResult(
    /* currently, only interested in the error response as success doesn't return a body*/
    val errorResponse: DuplicateAdjustmentErrorResponse? = null
  ) {
    val isError
      get() = errorResponse != null
  }

  private suspend fun createNomisSentenceAdjustmentMapping(
    nomisAdjustmentId: Long,
    nomisAdjustmentCategory: String,
    adjustmentId: String,
    mappingType: String,
    migrationId: String? = null
  ): CreateMappingResult {
    return webClient.post()
      .uri("/mapping/sentencing/adjustments")
      .bodyValue(
        SentencingAdjustmentNomisMapping(
          nomisAdjustmentId = nomisAdjustmentId,
          nomisAdjustmentCategory = nomisAdjustmentCategory,
          adjustmentId = adjustmentId,
          label = migrationId,
          mappingType = mappingType
        )
      )
      .retrieve()
      .bodyToMono(Unit::class.java)
      .map { CreateMappingResult() }
      .onErrorResume(WebClientResponseException.Conflict::class.java) {
        Mono.just(CreateMappingResult(it.getResponseBodyAs(DuplicateAdjustmentErrorResponse::class.java)))
      }
      .awaitFirstOrDefault(CreateMappingResult())
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

  suspend fun deleteNomisSentenceAdjustmentMapping(
    adjustmentId: String,
  ): Unit =
    webClient.delete()
      .uri("/mapping/sentencing/adjustments/adjustment-id/$adjustmentId")
      .retrieve()
      .awaitBody()
}

data class SentencingAdjustmentNomisMapping(
  val nomisAdjustmentId: Long,
  val nomisAdjustmentCategory: String,
  val adjustmentId: String,
  val label: String? = null,
  val mappingType: String
)

class DuplicateAdjustmentErrorResponse(
  val moreInfo: DuplicateAdjustmentErrorContent
)

data class DuplicateAdjustmentErrorContent(
  val duplicateAdjustment: SentencingAdjustmentNomisMapping,
  val existingAdjustment: SentencingAdjustmentNomisMapping,
)
