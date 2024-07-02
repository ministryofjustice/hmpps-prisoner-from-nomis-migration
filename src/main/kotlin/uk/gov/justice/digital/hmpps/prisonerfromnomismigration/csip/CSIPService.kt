package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateSaferCustodyScreeningOutcomeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CsipRecord
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SaferCustodyScreeningOutcome
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.awaitBodyOrNullWhenNotFound

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
      .header("Username", createdByUsername)
      .retrieve()
      .awaitBody()

  suspend fun deleteCSIP(csipReportId: String) =
    webClient.delete()
      .uri("/csip-records/{cspReportId}", csipReportId)
      .retrieve()
      .awaitBodyOrNullWhenNotFound<Unit>()

  suspend fun createCSIPSaferCustodyScreening(csipReportId: String, csipSCS: CreateSaferCustodyScreeningOutcomeRequest, createdByUsername: String): SaferCustodyScreeningOutcome =
    webClient.post()
      .uri("/csip-records/{cspReportId}/referral/safer-custody-screening", csipReportId)
      .header("Username", createdByUsername)
      .bodyValue(csipSCS)
      .retrieve()
      .awaitBody()

  suspend fun createCSIPFactor(csipReportId: String, csipFactor: CreateContributoryFactorRequest, createdByUsername: String): SaferCustodyScreeningOutcome =
    webClient.post()
      .uri("/csip-records/{cspReportId}/referral/contributory-factors", csipReportId)
      .header("Username", createdByUsername)
      .bodyValue(csipFactor)
      .retrieve()
      .awaitBody()
}
