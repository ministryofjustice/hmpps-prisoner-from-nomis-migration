package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.ContributoryFactor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncCsipRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrLogAndRethrowBadRequest

@Service
class CSIPDpsApiService(@Qualifier("csipApiWebClient") private val webClient: WebClient) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun migrateCSIP(syncRequest: SyncCsipRequest): SyncResponse =
    webClient.put()
      .uri("/sync/csip-records")
      .bodyValue(syncRequest)
      .retrieve()
      .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun syncCSIP(syncRequest: SyncCsipRequest): SyncResponse =
    webClient.put()
      .uri("/sync/csip-records")
      .bodyValue(syncRequest)
      .retrieve()
      .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun deleteCSIP(csipReportId: String) {
    webClient.delete()
      .uri("/csip-records/{csipReportId}", csipReportId)
      .header("Source", "NOMIS")
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createCSIPFactor(csipReportId: String, csipFactor: CreateContributoryFactorRequest, createdByUsername: String): ContributoryFactor =
    webClient.post()
      .uri("/csip-records/{csipReportId}/referral/contributory-factors", csipReportId)
      .header("Source", "NOMIS")
      .header("Username", createdByUsername)
      .bodyValue(csipFactor)
      .retrieve()
      .awaitBody()

  suspend fun updateCSIPFactor(csipFactorId: String, csipFactor: UpdateContributoryFactorRequest, updatedByUsername: String): ContributoryFactor =
    webClient.patch()
      .uri("/csip-records/referral/contributory-factors/{csipFactorId}", csipFactorId)
      .bodyValue(csipFactor)
      .header("Source", "NOMIS")
      .header("Username", updatedByUsername)
      .retrieve()
      .awaitBody()
}
