package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping

@Service
class SentencingAdjustmentsMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<SentencingAdjustmentNomisMapping>(domainUrl = "/mapping/sentencing/adjustments", webClient) {
  suspend fun findNomisSentencingAdjustmentMappingOrNull(
    nomisAdjustmentId: Long,
    nomisAdjustmentCategory: String,
  ): SentencingAdjustmentNomisMapping? = webClient.get()
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

  suspend fun findNomisSentencingAdjustmentMapping(
    nomisAdjustmentId: Long,
    nomisAdjustmentCategory: String,
  ): SentencingAdjustmentNomisMapping = webClient.get()
    .uri(
      "/mapping/sentencing/adjustments/nomis-adjustment-category/{nomisAdjustmentCategory}/nomis-adjustment-id/{nomisAdjustmentId}",
      nomisAdjustmentCategory,
      nomisAdjustmentId,
    )
    .retrieve()
    .awaitBody()

  suspend fun deleteNomisSentenceAdjustmentMapping(
    adjustmentId: String,
  ): Unit =
    webClient.delete()
      .uri("/mapping/sentencing/adjustments/adjustment-id/{adjustmentId}", adjustmentId)
      .retrieve()
      .awaitBody()
}

data class SentencingAdjustmentNomisMapping(
  val nomisAdjustmentId: Long,
  val nomisAdjustmentCategory: String,
  val adjustmentId: String,
  val label: String? = null,
  val mappingType: String,
)
