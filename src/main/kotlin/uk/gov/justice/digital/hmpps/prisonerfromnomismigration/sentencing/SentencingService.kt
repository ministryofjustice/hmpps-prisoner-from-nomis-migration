package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.adjustments.model.LegacyAdjustment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.adjustments.model.LegacyAdjustmentCreatedResponse

@Service
class SentencingService(@Qualifier("sentencingApiWebClient") private val webClient: WebClient) {
  companion object {
    const val LEGACY_CONTENT_TYPE = "application/vnd.nomis-offence+json"
  }

  suspend fun createSentencingAdjustment(sentencingAdjustment: LegacyAdjustment): LegacyAdjustmentCreatedResponse = webClient.post()
    .uri("/legacy/adjustments")
    .header("Content-Type", LEGACY_CONTENT_TYPE)
    .bodyValue(sentencingAdjustment)
    .retrieve()
    .awaitBody()

  suspend fun updateSentencingAdjustment(adjustmentId: String, sentencingAdjustment: LegacyAdjustment): Unit = webClient.put()
    .uri("/legacy/adjustments/{adjustmentId}", adjustmentId)
    .header("Content-Type", LEGACY_CONTENT_TYPE)
    .bodyValue(sentencingAdjustment)
    .retrieve()
    .awaitBody()

  suspend fun deleteSentencingAdjustment(adjustmentId: String) {
    webClient.delete()
      .uri("/legacy/adjustments/{adjustmentId}", adjustmentId)
      .header("Content-Type", LEGACY_CONTENT_TYPE)
      .retrieve()
      .awaitBodyOrNullWhenNotFound<Unit>()
  }

  suspend fun patchSentencingAdjustmentCurrentTerm(adjustmentId: String, sentencingAdjustment: LegacyAdjustment): Unit = webClient.patch()
    .uri("/legacy/adjustments/{adjustmentId}/current-term", adjustmentId)
    .header("Content-Type", LEGACY_CONTENT_TYPE)
    .bodyValue(sentencingAdjustment)
    .retrieve()
    .awaitBody()
}
