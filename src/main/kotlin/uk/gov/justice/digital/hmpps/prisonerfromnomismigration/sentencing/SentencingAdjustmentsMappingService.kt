package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.history.MigrationMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.SentencingMappingResourceApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.api.SentencingMappingResourceApi.NomisAdjustmentCategoryGetSentenceAdjustmentMappingGivenNomisId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.SentencingAdjustmentMappingDto

@Service
class SentencingAdjustmentsMappingService(@Qualifier("mappingApiWebClient") webClient: WebClient) : MigrationMapping<SentencingAdjustmentMappingDto>(domainUrl = "/mapping/sentencing/adjustments", webClient) {
  private val sentencingMappingResourceApi = SentencingMappingResourceApi(webClient)

  suspend fun findNomisSentencingAdjustmentMappingOrNull(
    nomisAdjustmentId: Long,
    nomisAdjustmentCategory: NomisAdjustmentCategoryGetSentenceAdjustmentMappingGivenNomisId,
  ): SentencingAdjustmentMappingDto? = sentencingMappingResourceApi.prepare(
    sentencingMappingResourceApi.getSentenceAdjustmentMappingGivenNomisIdRequestConfig(nomisAdjustmentId, nomisAdjustmentCategory),
  )
    .retrieve()
    .awaitBodyOrNullWhenNotFound()

  suspend fun deleteNomisSentenceAdjustmentMapping(
    adjustmentId: String,
  ): Unit = sentencingMappingResourceApi.prepare(
    sentencingMappingResourceApi.deleteSentenceAdjustmentMappingRequestConfig(adjustmentId),
  )
    .retrieve()
    .awaitBody()
}
