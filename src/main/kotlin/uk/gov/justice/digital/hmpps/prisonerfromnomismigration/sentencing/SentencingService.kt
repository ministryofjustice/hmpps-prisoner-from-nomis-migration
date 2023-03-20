package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.time.LocalDate

@Service
class SentencingService(@Qualifier("sentencingApiWebClient") private val webClient: WebClient) {
  suspend fun migrateSentencingAdjustment(sentencingAdjustment: SentencingAdjustment): CreateSentencingAdjustmentResponse =
    webClient.post()
      .uri("/legacy/adjustments/migration")
      .bodyValue(sentencingAdjustment)
      .retrieve()
      .awaitBody()

  suspend fun createSentencingAdjustment(sentencingAdjustment: SentencingAdjustment): CreateSentencingAdjustmentResponse =
    webClient.post()
      .uri("/legacy/adjustments")
      .bodyValue(sentencingAdjustment)
      .retrieve()
      .awaitBody()

  suspend fun updateSentencingAdjustment(adjustmentId: String, sentencingAdjustment: SentencingAdjustment): Unit =
    webClient.put()
      .uri("/legacy/adjustments/$adjustmentId")
      .bodyValue(sentencingAdjustment)
      .retrieve()
      .awaitBody()

  suspend fun deleteSentencingAdjustment(adjustmentId: String): Unit =
    webClient.delete()
      .uri("/legacy/adjustments/$adjustmentId")
      .retrieve()
      .awaitBody()
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SentencingAdjustment(
  val bookingId: Long,
  val offenderId: String,
  val sentenceSequence: Long? = null,
  val adjustmentType: String, // LegacyAdjustmentType enum in AdjustmentsApi
  @JsonFormat(pattern = "yyyy-MM-dd")
  val adjustmentDate: LocalDate?,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val adjustmentFromDate: LocalDate?,
  val adjustmentDays: Long,
  val comment: String?,
  val active: Boolean,
)

data class CreateSentencingAdjustmentResponse(
  val id: String,
)
