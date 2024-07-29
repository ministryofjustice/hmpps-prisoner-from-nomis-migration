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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.DecisionAndActions
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.Investigation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.Plan
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SaferCustodyScreeningOutcome
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateDecisionAndActionsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateInvestigationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdatePlanRequest

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
    webClient.patch()
      .uri("/csip-records/{csipReportId}/referral/investigation", csipReportId)
      .header("Source", "NOMIS")
      .header("Username", updatedByUsername)
      .bodyValue(investigationRequest)
      .retrieve()
      .awaitBody()

  suspend fun updateCSIPDecision(csipReportId: String, decisionRequest: UpdateDecisionAndActionsRequest, updatedByUsername: String): DecisionAndActions =
    webClient.patch()
      .uri("/csip-records/{csipReportId}/referral/decision-and-actions", csipReportId)
      .header("Source", "NOMIS")
      .header("Username", updatedByUsername)
      .bodyValue(decisionRequest)
      .retrieve()
      .awaitBody()

  suspend fun updateCSIPPlan(csipReportId: String, planRequest: UpdatePlanRequest, updatedByUsername: String): Plan =
    webClient.patch()
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

  suspend fun deleteCSIPFactor(csipFactorId: String) {
    webClient.delete()
      .uri("/csip-records/referral/contributory-factors/{csipFactorId}", csipFactorId)
      .header("Source", "NOMIS")
      .retrieve()
      .awaitBodilessEntity()
  }

  /*
// No create plan - do we ever know - should be upsert

  suspend fun updateCSIPPlan(csipReportId: String, csipPlan: UpdatePlanRequest, updatedByUsername: String): ContributoryFactor =
    webClient.patch()
      .uri("/csip-records/{csipReportId}/plan", csipReportId)
      .bodyValue(csipPlan)
      .header("Source", "NOMIS")
      .header("Username", updatedByUsername)
      .retrieve()
      .awaitBody()

  // No delete Plan - as part of report?

  suspend fun createCSIPPlanIdentifiedNeed(csipReportId: String, csipIdentifiedNeed: CreateIdentifiedNeedRequest, createdByUsername: String): ContributoryFactor =
    webClient.post()
      .uri("/csip-records/{csipReportId}/plan/identified-needs", csipReportId)
      .header("Source", "NOMIS")
      .header("Username", createdByUsername)
      .bodyValue(csipIdentifiedNeed)
      .retrieve()
      .awaitBody()

  suspend fun updateCSIPIdentifiedNeed(csipIdentifiedNeedId: String, csipIdentifiedNeed: UpdateIdentifiedNeedRequest, updatedByUsername: String): ContributoryFactor =
    webClient.patch()
      .uri("/csip-records/plan/identified-needs/{csipIdentifiedNeedId}", csipIdentifiedNeedId)
      .bodyValue(csipIdentifiedNeed)
      .header("Source", "NOMIS")
      .header("Username", updatedByUsername)
      .retrieve()
      .awaitBody()

  suspend fun deleteCSIPIdentifiedNeed(csipIdentifiedNeedId: String) {
    webClient.delete()
      .uri("/csip-records/plan/identified-needs/{csipIdentifiedNeedId}", csipIdentifiedNeedId)
      .header("Source", "NOMIS")
      .retrieve()
      .awaitBodilessEntity()
  }
   */
}
