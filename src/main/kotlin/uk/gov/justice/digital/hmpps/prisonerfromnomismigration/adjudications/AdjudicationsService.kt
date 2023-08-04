package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.AdjudicationMigrateDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateResponse

@Service
class AdjudicationsService(@Qualifier("adjudicationsApiWebClient") private val webClient: WebClient) {
  suspend fun createAdjudication(adjudicationMigrateRequest: AdjudicationMigrateDto): MigrateResponse =
    webClient.post()
      .uri("/reported-adjudications/migrate")
      .bodyValue(adjudicationMigrateRequest)
      .retrieve()
      .awaitBody()
}
