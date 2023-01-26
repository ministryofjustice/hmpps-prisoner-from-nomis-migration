package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDate

@Service
class SentencingService(@Qualifier("sentencingApiWebClient") private val webClient: WebClient) {
  suspend fun migrateSentencingAdjustment(sentenceAdjustment: CreateSentenceAdjustment): CreateSentencingAdjustmentResponse =
    webClient.post()
      .uri("/migration/sentencing/sentence-adjustments")
      .bodyValue(sentenceAdjustment)
      .retrieve()
      .bodyToMono(CreateSentencingAdjustmentResponse::class.java)
      .awaitSingle()!!
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateSentenceAdjustment(
  // will change once Sentencing API implemented
  val bookingId: Long,
  val sentenceSequence: Long,
  val sentenceAdjustmentType: String,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val adjustmentDate: LocalDate,
  @JsonFormat(pattern = "yyyy-MM-dd")
  val adjustmentFromDate: LocalDate?,
  val adjustmentDays: Long,
  val comment: String?,
  val active: Boolean,
)

data class CreateSentencingAdjustmentResponse(
  val id: Long,
)
