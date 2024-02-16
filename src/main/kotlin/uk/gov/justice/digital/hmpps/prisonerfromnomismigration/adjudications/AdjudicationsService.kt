package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.AdjudicationMigrateDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.MigrateResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.model.ReportedAdjudicationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotAcceptable
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound

@Service
class AdjudicationsService(@Qualifier("adjudicationsApiWebClient") private val webClient: WebClient) {
  suspend fun createAdjudication(adjudicationMigrateRequest: AdjudicationMigrateDto): MigrateResponse? =
    webClient.post()
      .uri("/reported-adjudications/migrate")
      .bodyValue(adjudicationMigrateRequest)
      .retrieve()
      .awaitBodyOrNullWhenNotAcceptable()

  suspend fun getCharge(chargeNumber: String, prisonId: String): ReportedAdjudicationResponse? {
    return webClient.get()
      .uri("/reported-adjudications/{chargeNumber}/v2", chargeNumber)
      .header("Active-Caseload", prisonId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound()
  }
}
