package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import org.openapitools.client.infrastructure.RequestConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.adjustments.api.LegacyControllerApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.adjustments.model.LegacyAdjustment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.adjustments.model.LegacyAdjustmentCreatedResponse
import java.util.UUID

@Service
class SentencingService(@Qualifier("sentencingApiWebClient") webClient: WebClient) {
  private val legacyControllerApi = LegacyControllerApi(webClient)

  suspend fun createSentencingAdjustment(sentencingAdjustment: LegacyAdjustment): LegacyAdjustmentCreatedResponse = legacyControllerApi.prepare(
    legacyControllerApi.createRequestConfig(sentencingAdjustment).apply {
      setLegacyContentTypeHeader()
    },
  )
    .retrieve()
    .awaitBody()

  suspend fun updateSentencingAdjustment(adjustmentId: String, sentencingAdjustment: LegacyAdjustment): Unit = legacyControllerApi.prepare(
    legacyControllerApi.updateRequestConfig(UUID.fromString(adjustmentId), sentencingAdjustment).apply {
      setLegacyContentTypeHeader()
    },
  )
    .retrieve()
    .awaitBody()

  suspend fun deleteSentencingAdjustment(adjustmentId: String) {
    legacyControllerApi.prepare(
      legacyControllerApi.deleteRequestConfig(UUID.fromString(adjustmentId)).apply {
        setLegacyContentTypeHeader()
      },
    )
      .retrieve()
      .awaitBodyOrNullWhenNotFound<Unit>()
  }

  private fun <T> RequestConfig<T>.setLegacyContentTypeHeader() {
    headers["Content-Type"] = "application/vnd.nomis-offence+json"
  }
}
