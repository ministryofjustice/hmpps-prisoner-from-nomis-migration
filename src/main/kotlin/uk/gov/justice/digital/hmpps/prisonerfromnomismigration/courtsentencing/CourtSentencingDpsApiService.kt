package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtCase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtCaseResponse

@Service
class CourtSentencingDpsApiService(@Qualifier("courtSentencingApiWebClient") private val webClient: WebClient) {
  suspend fun createCourtCase(courtCase: CreateCourtCase): CreateCourtCaseResponse =
    webClient
      .post()
      .uri("/court-case")
      .bodyValue(courtCase)
      .retrieve()
      .awaitBody()

  suspend fun updateCourtCase(courtCaseId: String, courtCase: CreateCourtCase): CreateCourtCaseResponse =
    webClient
      .put()
      .uri("/court-case/{courtCaseId}", courtCaseId)
      .bodyValue(courtCase)
      .retrieve()
      .awaitBody()

  suspend fun deleteCourtCase(courtCaseId: String) =
    webClient
      .delete()
      .uri("/court-case/{courtCaseId}", courtCaseId)
      .retrieve()
      .awaitBodilessEntity()
}
