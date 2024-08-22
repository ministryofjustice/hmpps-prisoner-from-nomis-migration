package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.OK
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.ContributoryFactor
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateReferralRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateSaferCustodyScreeningOutcomeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CsipRecord
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.DecisionAndActions
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.Interview
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.Investigation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.Plan
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.ReferenceData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.Referral
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SaferCustodyScreeningOutcome
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateDecisionAndActionsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateInvestigationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdatePlanRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateReferral
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class CSIPApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val csipApi = CSIPApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    csipApi.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    csipApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    csipApi.stop()
  }
}

class CSIPApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8088

    fun dpsMigrateCsipRecordRequest() =
      CreateCsipRecordRequest(
        logCode = "ASI-001",
        referral =
        CreateReferralRequest(
          incidentDate = LocalDate.parse("2024-06-12"),
          incidentTypeCode = "INT",
          incidentLocationCode = "LIB",
          referredBy = "JIM_ADM",
          refererAreaCode = "EDU",
          incidentInvolvementCode = "PER",
          descriptionOfConcern = "There was a worry about the offender",
          knownReasons = "known reasons details go in here",
          contributoryFactors = listOf(),
          incidentTime = "10:32:12",
          isProactiveReferral = true,
          isStaffAssaulted = true,
          assaultedStaffName = "Fred Jones",
          otherInformation = "other information goes in here",
          isSaferCustodyTeamInformed = CreateReferralRequest.IsSaferCustodyTeamInformed.NO,
          isReferralComplete = true,
        ),
      )

    fun dpsCreateCsipRecordRequest() =
      CreateCsipRecordRequest(
        logCode = "ASI-001",
        referral =
        CreateReferralRequest(
          incidentDate = LocalDate.parse("2024-06-12"),
          incidentTypeCode = "INT",
          incidentLocationCode = "LIB",
          referredBy = "JIM_ADM",
          refererAreaCode = "EDU",
          contributoryFactors = listOf(),
          incidentTime = "10:32:12",
          isProactiveReferral = true,
          isStaffAssaulted = true,
          assaultedStaffName = "Fred Jones",
          isSaferCustodyTeamInformed = CreateReferralRequest.IsSaferCustodyTeamInformed.DO_NOT_KNOW,
        ),
      )

    fun dpsUpdateCsipReferralRequest() =
      UpdateCsipRecordRequest(
        logCode = "ASI-001",
        UpdateReferral(
          incidentDate = LocalDate.parse("2024-06-12"),
          incidentTypeCode = "INT",
          incidentLocationCode = "LIB",
          referredBy = "JIM_ADM",
          refererAreaCode = "EDU",
          incidentTime = "10:32:12",
          isProactiveReferral = true,
          isStaffAssaulted = true,
          assaultedStaffName = "Fred Jones",
          isSaferCustodyTeamInformed = UpdateReferral.IsSaferCustodyTeamInformed.DO_NOT_KNOW,
        ),
      )

    fun dpsCSIPReport(dpsCSIPReportId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5", logNumber: String? = null) = CsipRecord(
      recordUuid = UUID.fromString(dpsCSIPReportId),
      prisonNumber = "1234",
      createdAt = LocalDateTime.parse("2024-03-29T11:32:15"),
      createdBy = "JIM_SMITH",
      createdByDisplayName = "Jim Smith",
      referral =
      Referral(
        incidentDate = LocalDate.parse("2024-03-27"),
        incidentType = ReferenceData(code = "INT", description = "Intimidation"),
        incidentLocation = ReferenceData(code = "LIB", description = "Library"),
        referredBy = "Jim Smith",
        refererArea = ReferenceData(code = "EDU", description = "Education"),
        incidentInvolvement = ReferenceData(code = "PER", description = "Perpetrator"),
        descriptionOfConcern = "Needs guidance",
        knownReasons = "Fighting",
        contributoryFactors = listOf(),
        incidentTime = null,
        isProactiveReferral = null,
        isStaffAssaulted = null,
        assaultedStaffName = null,
        otherInformation = null,
        isSaferCustodyTeamInformed = Referral.IsSaferCustodyTeamInformed.NO,
        isReferralComplete = null,
        investigation = null,
        saferCustodyScreeningOutcome = null,
        decisionAndActions = null,
      ),
      prisonCodeWhenRecorded = null,
      logCode = logNumber,
      lastModifiedAt = null,
      lastModifiedBy = null,
      lastModifiedByDisplayName = null,
      plan = null,
    )

    fun dpsCreateSaferCustodyScreeningOutcomeRequest() =
      CreateSaferCustodyScreeningOutcomeRequest(
        outcomeTypeCode = "CUR",
        date = LocalDate.parse("2024-04-08"),
        reasonForDecision = "There is a reason for the decision - it goes here",
        recordedBy = "BOB_ADM",
        recordedByDisplayName = "Bob Admin",
      )

    fun dpsSaferCustodyScreening() =
      SaferCustodyScreeningOutcome(
        outcome = ReferenceData(code = "CUR", description	= "Progress to CSIP", listSequence = 1),
        recordedBy = "FRED_ADM",
        recordedByDisplayName = "FRED_ADM",
        date = LocalDate.parse("2024-04-08"),
        reasonForDecision = "There is a reason for the decision - it goes here",
      )

    fun dpsUpdateInvestigationRequest() =
      UpdateInvestigationRequest(
        staffInvolved = "some people",
        evidenceSecured = "A piece of pipe",
        occurrenceReason = "bad behaviour",
        personsUsualBehaviour = "Good person",
        personsTrigger = "missed meal",
        protectiveFactors = "ensure taken to canteen",
      )
    fun dpsCSIPInvestigation() =
      Investigation(
        evidenceSecured = "A piece of pipe",
        occurrenceReason = "bad behaviour",
        personsUsualBehaviour = "Good person",
        personsTrigger = "missed meal",
        protectiveFactors = "ensure taken to canteen",
        interviews = listOf(
          Interview(
            interviewUuid = UUID.randomUUID(),
            interviewee = "Bill Black",
            interviewDate = LocalDate.parse("2024-06-06"),
            intervieweeRole = ReferenceData(
              code = "WITNESS",
              description = "Witness",
              listSequence = 1,
            ),
            createdAt = LocalDateTime.parse("2024-04-04T15:12:32.00462"),
            createdBy = "AA_ADM",
            createdByDisplayName = "Albert Amber",
            interviewText = "Saw a pipe in his hand",
            lastModifiedAt = null,
            lastModifiedBy = null,
            lastModifiedByDisplayName = null,
          ),
        ),
      )

    fun dpsUpdateDecisionRequest() =
      UpdateDecisionAndActionsRequest(
        outcomeTypeCode = "CUR",
        conclusion = null,
        signedOffByRoleCode = "CUSTMAN",
        recordedBy = "FRED_ADM",
        recordedByDisplayName = "Fred Admin",
        date = LocalDate.parse("2024-04-08"),
        nextSteps = null,
        actions =
        setOf(
          UpdateDecisionAndActionsRequest.Actions.NonAssociationsUpdated,
          UpdateDecisionAndActionsRequest.Actions.ObservationBook,
          UpdateDecisionAndActionsRequest.Actions.ServiceReferral,
        ),
        actionOther = "Some other info here",
      )
    fun dpsCSIPDecision() =
      DecisionAndActions(
        outcome = ReferenceData(
          code = "CUR",
          description = "Current",
          listSequence = 1,

        ),
        actions =
        setOf(
          DecisionAndActions.Actions.NonAssociationsUpdated,
          DecisionAndActions.Actions.ObservationBook,
          DecisionAndActions.Actions.ServiceReferral,
        ),

        conclusion = null,
        recordedBy = "FRED_ADM",
        recordedByDisplayName = "Fred Admin",
        date = LocalDate.parse("2024-04-08"),
        nextSteps = null,
        actionOther = "Some other info here",
      )

    fun dpsUpdatePlanRequest() =
      UpdatePlanRequest(
        caseManager = "C Jones",
        reasonForPlan = "helper",
        firstCaseReviewDate = LocalDate.parse("2024-04-15"),
      )
    fun dpsCSIPPlan() =
      Plan(
        caseManager = "C Jones",
        reasonForPlan = "helper",
        firstCaseReviewDate = LocalDate.parse("2024-04-15"),
        identifiedNeeds = listOf(),
        reviews = listOf(),
      )

    fun dpsCreateContributoryFactorRequest() =
      CreateContributoryFactorRequest(
        factorTypeCode = "BUL",
        comment = "Offender causes trouble",
      )
    fun dpsUpdateContributoryFactorRequest() =
      UpdateContributoryFactorRequest(
        factorTypeCode = "BUL",
        comment = "Offender causes trouble",
      )

    fun dpsCSIPFactor(dpsCsipFactorId: String) =
      ContributoryFactor(
        factorUuid = UUID.fromString(dpsCsipFactorId),
        factorType = ReferenceData(code = "BUL", description = "Bullying"),
        createdAt = LocalDateTime.parse("2024-03-29T11:32:16"),
        createdBy = "JIM_ADM",
        createdByDisplayName = "Jim Admin",
        comment = "Offender causes trouble",
      )
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubCSIPMigrate(dpsCSIPId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5", offenderNo: String = "A1234BC") {
    stubFor(
      post("/migrate/prisoners/$offenderNo/csip-records").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(dpsCSIPReport(dpsCSIPReportId = dpsCSIPId)),
      ),
    )
  }

  fun stubCSIPInsert(dpsCSIPId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5", offenderNo: String = "A1234BC") {
    stubFor(
      post("/prisoners/$offenderNo/csip-records").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(dpsCSIPReport(dpsCSIPReportId = dpsCSIPId)),
      ),
    )
  }

  fun stubCSIPUpdate(dpsCSIPId: String) {
    stubFor(
      patch("/csip-records/$dpsCSIPId/referral").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(OK.value())
          .withBody(dpsCSIPReport(dpsCSIPReportId = dpsCSIPId)),
      ),
    )
  }
  fun stubCSIPDelete(dpsCSIPId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") {
    stubFor(
      delete("/csip-records/$dpsCSIPId").willReturn(
        aResponse()
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCSIPDeleteNotFound(status: HttpStatus = HttpStatus.NOT_FOUND) {
    stubDeleteErrorResponse(status = status, url = "/csip-records/\\S+")
  }

  // /////////////// CSIP Safer Custody Screening
  fun stubCSIPSCSInsert(dpsCSIPId: String) {
    stubFor(
      post("/csip-records/$dpsCSIPId/referral/safer-custody-screening").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(dpsSaferCustodyScreening()),
      ),
    )
  }

  // /////////////// CSIP Factor
  fun stubCSIPFactorInsert(dpsCSIPReportId: String, dpsCSIPFactorId: String) {
    stubFor(
      post("/csip-records/$dpsCSIPReportId/referral/contributory-factors").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(dpsCSIPFactor(dpsCSIPFactorId)),
      ),
    )
  }
  fun stubCSIPFactorUpdate(dpsCSIPFactorId: String) {
    stubFor(
      patch("/csip-records/referral/contributory-factors/$dpsCSIPFactorId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(dpsCSIPFactor(dpsCSIPFactorId)),
      ),
    )
  }
  fun stubCSIPFactorDelete(dpsCSIPFactorId: String) {
    stubDelete("/csip-records/referral/contributory-factors/$dpsCSIPFactorId")
  }

  fun stubCSIPFactorDeleteNotFound(status: HttpStatus = HttpStatus.NOT_FOUND) {
    stubDeleteErrorResponse(status = status, url = "/csip-records/referral/contributory-factors/\\S+")
  }

  // /////////////// CSIP Interview
  fun stubDeleteCSIPInterview(dpsCSIPInterviewId: String) {
    stubDelete("/csip-records/referral/investigation/interviews/$dpsCSIPInterviewId")
  }

  fun stubDeleteCSIPInterviewNotFound(status: HttpStatus = HttpStatus.NOT_FOUND) {
    stubDeleteErrorResponse(status = status, url = "/csip-records/referral/investigation/interviews/\\S+")
  }

  // /////////////// CSIP Plan
  fun stubCSIPPlanDelete(dpsCSIPPlanId: String) {
    stubDelete("/csip-records/plan/identified-needs/$dpsCSIPPlanId")
  }

  fun stubCSIPPlanDeleteNotFound(status: HttpStatus = HttpStatus.NOT_FOUND) {
    stubFor(
      delete(WireMock.urlPathMatching("/csip-records/plan/identified-needs/\\S+"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value()),
        ),
    )
  }

  // /////////////// CSIP Investigation
  fun stubCSIPInvestigationUpdate(dpsCSIPId: String) {
    stubFor(
      put("/csip-records/$dpsCSIPId/referral/investigation").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(OK.value())
          .withBody(dpsCSIPInvestigation()),
      ),
    )
  }

  fun stubCSIPUpdateDecision(dpsCSIPId: String) {
    stubFor(
      put("/csip-records/$dpsCSIPId/referral/decision-and-actions").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(OK.value())
          .withBody(dpsCSIPDecision()),
      ),
    )
  }

  fun stubCSIPUpdatePlan(dpsCSIPId: String) {
    stubFor(
      put("/csip-records/$dpsCSIPId/plan").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(OK.value())
          .withBody(dpsCSIPPlan()),
      ),
    )
  }

  private fun stubDelete(url: String) {
    stubFor(
      delete(urlPathMatching(url)).willReturn(
        aResponse()
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  private fun stubDeleteErrorResponse(status: HttpStatus, url: String, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingApi.stubFor(
      delete(urlPathMatching(url))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun createCSIPMigrationCount() =
    findAll(postRequestedFor(urlMatching("/migrate/prisoners/.*"))).count()

  fun createCSIPSyncCount() =
    findAll(postRequestedFor(urlMatching("/prisoners/.*"))).count()

  fun createCSIPFactorSyncCount() =
    findAll(postRequestedFor(urlMatching("/csip-records/.*/referral/contributory-factors"))).count()

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder =
    this.withBody(objectMapper.writeValueAsString(body))
}
