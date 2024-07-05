package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodilessEntity
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.ContributoryFactor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateSaferCustodyScreeningOutcomeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CsipRecord
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SaferCustodyScreeningOutcome
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateContributoryFactorRequest

@Service
class CSIPService(@Qualifier("csipApiWebClient") private val webClient: WebClient) {

  suspend fun migrateCSIP(offenderNo: String, csipReport: CreateCsipRecordRequest): CsipRecord =
    webClient.post()
      .uri("/migrate/prisoners/{offenderNo}/csip-records", offenderNo)
      .bodyValue(csipReport)
      .retrieve()
      .awaitBody()

  suspend fun createCSIPReport(offenderNo: String, csipReport: CreateCsipRecordRequest, createdByUsername: String): CsipRecord =
    webClient.post()
      .uri("/prisoners/{offenderNo}/csip-records", offenderNo)
      .bodyValue(csipReport)
      .header("Source", "NOMIS")
      .header("Username", createdByUsername)
      .retrieve()
      .awaitBody()

  suspend fun deleteCSIP(csipReportId: String) {
    webClient.delete()
      .uri("/csip-records/{cspReportId}", csipReportId)
      .header("Source", "NOMIS")
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createCSIPSaferCustodyScreening(csipReportId: String, csipSCS: CreateSaferCustodyScreeningOutcomeRequest, createdByUsername: String): SaferCustodyScreeningOutcome =
    webClient.post()
      .uri("/csip-records/{cspReportId}/referral/safer-custody-screening", csipReportId)
      .header("Source", "NOMIS")
      .header("Username", createdByUsername)
      .bodyValue(csipSCS)
      .retrieve()
      .awaitBody()

  suspend fun createCSIPFactor(csipReportId: String, csipFactor: CreateContributoryFactorRequest, createdByUsername: String): ContributoryFactor =
    webClient.post()
      .uri("/csip-records/{cspReportId}/referral/contributory-factors", csipReportId)
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

  suspend fun deleteCSIPFactor(csipFactorId: String) {
    webClient.delete()
      .uri("/csip-records/referral/contributory-factors/{csipFactorId}", csipFactorId)
      .header("Source", "NOMIS")
      .retrieve()
      .awaitBodilessEntity()
  }
}
