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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateSaferCustodyScreeningOutcomeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CsipRecord
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.DecisionAndActions
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.Investigation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.Plan
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SaferCustodyScreeningOutcome
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncCsipRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateInvestigationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdatePlanRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpsertDecisionAndActionsRequest
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

  suspend fun syncCSIP(syncRequest: SyncCsipRequest, createdByUsername: String): SyncResponse =
    webClient.put()
      .uri("/sync/csip-records")
      .bodyValue(syncRequest)
      .header("Source", "NOMIS")
      .header("Username", createdByUsername)
      .retrieve()
      .awaitBodyOrLogAndRethrowBadRequest()

  suspend fun updateCSIPReferral(csipReportId: String, csipReport: UpdateCsipRecordRequest, updatedByUsername: String): CsipRecord =
    webClient.patch()
      .uri("/csip-records/{csipReportId}/referral", csipReportId)
      .header("Source", "NOMIS")
      .header("Username", updatedByUsername)
      .bodyValue(csipReport)
      .retrieve()
      .awaitBody()

  suspend fun deleteCSIP(csipReportId: String) {
    webClient.delete()
      .uri("/csip-records/{csipReportId}", csipReportId)
      .header("Source", "NOMIS")
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun createCSIPSaferCustodyScreening(csipReportId: String, csipSCS: CreateSaferCustodyScreeningOutcomeRequest, createdByUsername: String): SaferCustodyScreeningOutcome =
    webClient.post()
      .uri("/csip-records/{csipReportId}/referral/safer-custody-screening", csipReportId)
      .header("Source", "NOMIS")
      .header("Username", createdByUsername)
      .bodyValue(csipSCS)
      .retrieve()
      .awaitBody()

  suspend fun updateCSIPInvestigation(csipReportId: String, investigationRequest: UpdateInvestigationRequest, updatedByUsername: String): Investigation =
    webClient.put()
      .uri("/csip-records/{csipReportId}/referral/investigation", csipReportId)
      .header("Source", "NOMIS")
      .header("Username", updatedByUsername)
      .bodyValue(investigationRequest)
      .retrieve()
      .awaitBody()

  suspend fun updateCSIPDecision(csipReportId: String, decisionRequest: UpsertDecisionAndActionsRequest, updatedByUsername: String): DecisionAndActions =
    webClient.put()
      .uri("/csip-records/{csipReportId}/referral/decision-and-actions", csipReportId)
      .header("Source", "NOMIS")
      .header("Username", updatedByUsername)
      .bodyValue(decisionRequest)
      .retrieve()
      .awaitBody()

  suspend fun updateCSIPPlan(csipReportId: String, planRequest: UpdatePlanRequest, updatedByUsername: String): Plan =
    webClient.put()
      .uri("/csip-records/{csipReportId}/plan", csipReportId)
      .header("Source", "NOMIS")
      .header("Username", updatedByUsername)
      .bodyValue(planRequest)
      .retrieve()
      .awaitBody()

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
