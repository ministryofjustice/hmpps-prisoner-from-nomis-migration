package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpsertDecisionAndActionsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpsertInvestigationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpsertPlanRequest

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
      .bodyToMono(SyncResponse::class.java)
      .onErrorResume(WebClientResponseException.BadRequest::class.java) {
        log.error("Received Bad Request (400) with body " + it.responseBodyAsString)
        throw it
      }
      .awaitSingle()

  suspend fun syncCSIP(syncRequest: SyncCsipRequest, createdByUsername: String): SyncResponse =
    webClient.put()
      .uri("/sync/csip-records")
      .bodyValue(syncRequest)
      .header("Source", "NOMIS")
      .header("Username", createdByUsername)
      .retrieve()
      .bodyToMono(SyncResponse::class.java)
      .onErrorResume(WebClientResponseException.BadRequest::class.java) {
        log.error("Received Bad Request (400) with body " + it.responseBodyAsString)
        throw it
      }
      .awaitSingle()

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

  suspend fun deleteCSIPPlan(csipPlanId: String) {
    webClient.delete()
      .uri("/csip-records/plan/identified-needs/{csipPlanId}", csipPlanId)
      .header("Source", "NOMIS")
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun deleteCSIPInterview(csipInterviewId: String) {
    webClient.delete()
      .uri("/csip-records/referral/investigation/interviews/{csipInterviewId}", csipInterviewId)
      .header("Source", "NOMIS")
      .retrieve()
      .awaitBodilessEntity()
  }

  suspend fun deleteCSIPAttendee(csipAttendeeId: String) {
    webClient.delete()
      .uri("/csip-records/plan/reviews/attendees/{csipAttendeeId}", csipAttendeeId)
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

  suspend fun updateCSIPInvestigation(csipReportId: String, investigationRequest: UpsertInvestigationRequest, updatedByUsername: String): Investigation =
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

  suspend fun updateCSIPPlan(csipReportId: String, planRequest: UpsertPlanRequest, updatedByUsername: String): Plan =
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

  suspend fun deleteCSIPFactor(csipFactorId: String) {
    webClient.delete()
      .uri("/csip-records/referral/contributory-factors/{csipFactorId}", csipFactorId)
      .header("Source", "NOMIS")
      .retrieve()
      .awaitBodilessEntity()
  }
}
