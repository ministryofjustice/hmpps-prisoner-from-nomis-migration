package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDateTime

@Service
class SentencingService(@Qualifier("sentencingApiWebClient") private val webClient: WebClient) {
  fun migrateSentenceAdjustment(sentenceAdjustment: CreateSentenceAdjustment): CreateSentenceAdjustmentResponse =
    webClient.post()
      .uri("/tempSentenceUrl")
      .bodyValue(sentenceAdjustment)
      .retrieve()
      .bodyToMono(CreateSentenceAdjustmentResponse::class.java)
      .block()!!
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateSentenceAdjustment(
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
  val date: LocalDateTime,
)

data class CreateSentenceAdjustmentResponse(
  val id: Long,
)
