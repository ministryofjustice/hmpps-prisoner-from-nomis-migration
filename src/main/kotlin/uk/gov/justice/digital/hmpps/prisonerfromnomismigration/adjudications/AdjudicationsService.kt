package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Service
class AdjudicationsService(@Qualifier("adjudicationsApiWebClient") private val webClient: WebClient) {
  suspend fun createAdjudication(adjudicationMigrateRequest: AdjudicationMigrateRequest): AdjudicationMigrateResponse =
    webClient.post()
      .uri("/legacy/adjudications/migration")
      .bodyValue(adjudicationMigrateRequest)
      .retrieve()
      .awaitBody() // todo doesn't need an ID for adjudication but lower-level entities may be mapped
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AdjudicationMigrateRequest(
  val offenderNo: String,
  val adjudicationNumber: Long,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class AdjudicationMigrateResponse(
  val adjudicationNumber: Long,
)
