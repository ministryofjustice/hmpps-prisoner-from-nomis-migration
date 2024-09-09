package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.ResponseMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SaferCustodyScreeningOutcome
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncAttendeeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncCsipRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncDecisionAndActionsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncInterviewRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncInvestigationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncNeedRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncPlanRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncReferralRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncReviewRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SyncScreeningOutcomeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateContributoryFactorRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpdateReferral
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpsertDecisionAndActionsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpsertInvestigationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.UpsertPlanRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class CSIPApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val csipDpsApi = CSIPApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    csipDpsApi.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    csipDpsApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    csipDpsApi.stop()
  }
}

class CSIPApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8088

    fun dpsSyncCsipRequest() =
      SyncCsipRequest(
        legacyId = 1234,
        id = null,
        logCode = "ASI-001",
        prisonNumber = "MDI",
        prisonCodeWhenRecorded = null,
        activeCaseloadId = "MDI",
        actionedAt = LocalDateTime.parse("2024-04-01T10:32:12.867081"),
        actionedBy = "",
        actionedByDisplayName = "",
        createdAt = LocalDateTime.parse("2024-04-01T10:32:12.867081"),
        createdBy = "JSMITH",
        createdByDisplayName = "JSMITH",
        lastModifiedAt = null,
        lastModifiedBy = null,
        lastModifiedByDisplayName = null,
        referral = SyncReferralRequest(
          incidentDate = LocalDate.parse("2024-06-12"),
          incidentTypeCode = "INT",
          incidentLocationCode = "LIB",
          referredBy = "JIM_ADM",
          referralDate = LocalDate.parse("2024-04-04"),
          refererAreaCode = "EDU",
          isSaferCustodyTeamInformed = SyncReferralRequest.IsSaferCustodyTeamInformed.NO,
          contributoryFactors = listOf(
            SyncContributoryFactorRequest(
              factorTypeCode = "BUL",
              legacyId = 43,
              createdAt = LocalDateTime.parse("2024-04-01T10:00:00"),
              createdBy = "JSMITH",
              createdByDisplayName = "JSMITH",
              comment = "Offender causes trouble",
              id = null,
              lastModifiedAt = null,
              lastModifiedBy = null,
              lastModifiedByDisplayName = null,
            ),
          ),
          incidentTime = "10:32:12",
          isProactiveReferral = true,
          isStaffAssaulted = true,
          assaultedStaffName = "Fred Jones",
          incidentInvolvementCode = "PER",
          descriptionOfConcern = "There was a worry about the offender",
          knownReasons = "known reasons details go in here",
          otherInformation = "other information goes in here",
          isReferralComplete = true,
          completedDate = LocalDate.parse("2024-04-04"),
          completedBy = "JIM_ADM",
          completedByDisplayName = "",
          saferCustodyScreeningOutcome = SyncScreeningOutcomeRequest(
            outcomeTypeCode = "CUR",
            reasonForDecision = "There is a reason for the decision - it goes here",
            date = LocalDate.parse("2024-04-08"),
            recordedBy = "FRED_ADM",
            recordedByDisplayName = "Fred Admin",
          ),
          investigation = SyncInvestigationRequest(
            interviews = listOf(
              SyncInterviewRequest(
                legacyId = 3343,
                id = null,
                interviewee = "Bill Black",
                interviewDate = LocalDate.parse("2024-06-06"),
                intervieweeRoleCode = "WITNESS",
                createdAt = LocalDateTime.parse("2024-04-04T15:12:32.004620"),
                createdBy = "AA_ADM",
                createdByDisplayName = "ADAM SMITH",
                interviewText = "Saw a pipe in his hand",
                lastModifiedAt = LocalDateTime.parse("2024-08-12T11:32:15"),
                lastModifiedBy = "BB_ADM",
                lastModifiedByDisplayName = "Bebe SMITH",
              ),
            ),
            staffInvolved = "some people",
            evidenceSecured = "A piece of pipe",
            occurrenceReason = "bad behaviour",
            personsUsualBehaviour = "Good person",
            personsTrigger = "missed meal",
            protectiveFactors = "ensure taken to canteen",
          ),
          decisionAndActions =
          SyncDecisionAndActionsRequest(
            outcomeTypeCode = "OPE",
            actions = setOf(
              SyncDecisionAndActionsRequest.Actions.NON_ASSOCIATIONS_UPDATED,
              SyncDecisionAndActionsRequest.Actions.OBSERVATION_BOOK,
              SyncDecisionAndActionsRequest.Actions.SERVICE_REFERRAL,
            ),
            conclusion = "Offender needs help",
            signedOffByRoleCode = "CUSTMAN",
            date = LocalDate.parse("2024-04-08"),
            recordedBy = "FRED_ADM",
            recordedByDisplayName = "Fred Admin",
            nextSteps = null,
            actionOther = "Some other info here",
          ),
        ),
        plan = SyncPlanRequest(
          caseManager = "C Jones",
          reasonForPlan = "helper",
          firstCaseReviewDate = LocalDate.parse("2024-04-15"),
          identifiedNeeds = listOf(
            SyncNeedRequest(
              id = null,
              legacyId = 65,
              identifiedNeed = "they need help",
              responsiblePerson = "Jason", intervention = "dd",
              progression = "there was some improvement",
              targetDate = LocalDate.parse("2024-08-20"),
              closedDate = LocalDate.parse("2024-04-17"),
              createdDate = LocalDate.parse("2024-04-16"),

              createdAt = LocalDateTime.parse("2024-03-16T11:32:15"),
              createdBy = "JSMITH",
              createdByDisplayName = "JOHN SMITH",
              lastModifiedAt = null,
              lastModifiedBy = null,
              lastModifiedByDisplayName = null,
            ),
          ),
          reviews = listOf(
            SyncReviewRequest(
              legacyId = 67,
              id = null,
              reviewDate = LocalDate.parse("2024-04-01"),
              nextReviewDate = null,
              csipClosedDate = LocalDate.parse("2024-04-16"),
              summary = null,
              recordedBy = "JSMITH",
              recordedByDisplayName = "JOHN SMITH",

              createdAt = LocalDateTime.parse("2024-04-01T10:00"),
              createdBy = "JSMITH",
              createdByDisplayName = "JOHN SMITH",
              lastModifiedAt = null,
              lastModifiedBy = null,
              lastModifiedByDisplayName = null,

              actions = setOf(
                SyncReviewRequest.Actions.REMAIN_ON_CSIP,
                SyncReviewRequest.Actions.CLOSE_CSIP,
              ),
              attendees = listOf(),
            ),
          ),
        ),
      )

    fun dpsSyncCsipRequestFull() =
      SyncCsipRequest(
        legacyId = 1234,
        id = UUID.randomUUID(),
        logCode = "ASI-001",
        prisonNumber = "MDI",
        prisonCodeWhenRecorded = "ASI",
        activeCaseloadId = "MDI",
        actionedAt = LocalDateTime.parse("2024-08-11T11:32:15"),
        actionedBy = "",
        actionedByDisplayName = "",
        createdAt = LocalDateTime.parse("2024-08-12T11:32:15"),
        createdBy = "JANE_JONES",
        createdByDisplayName = "Jane Jones",
        lastModifiedAt = LocalDateTime.parse("2024-08-12T11:32:15"),
        lastModifiedBy = "JOHN_SMITH",
        lastModifiedByDisplayName = "John Smith",

        referral = SyncReferralRequest(
          incidentDate = LocalDate.parse("2024-06-12"),
          incidentTime = "10:32:12",
          incidentTypeCode = "INT",
          incidentLocationCode = "LIB",
          referredBy = "JIM_ADM",
          referralDate = LocalDate.parse("2024-04-04"),
          refererAreaCode = "EDU",
          isSaferCustodyTeamInformed = SyncReferralRequest.IsSaferCustodyTeamInformed.NO,
          isProactiveReferral = true,
          isStaffAssaulted = true,
          assaultedStaffName = "Fred Jones",
          incidentInvolvementCode = "PER",
          descriptionOfConcern = "There was a worry about the offender",
          knownReasons = "known reasons details go in here",
          otherInformation = "other information goes in here",
          isReferralComplete = true,
          completedDate = null,
          completedBy = null,
          completedByDisplayName = "",

          contributoryFactors = listOf(),
          saferCustodyScreeningOutcome =
          SyncScreeningOutcomeRequest(
            outcomeTypeCode = "CUR",
            reasonForDecision = "There is a reason for the decision - it goes here",
            date = LocalDate.parse("2024-04-08"),
            recordedBy = "FRED_ADM",
            recordedByDisplayName = "Fred Admin",
          ),
          investigation = SyncInvestigationRequest(
            interviews = listOf(
              SyncInterviewRequest(
                legacyId = 3343,
                id = UUID.fromString("53f6bdc9-de59-41b6-9774-52699c6f8e64"),
                interviewee = "Bill Black",
                interviewDate = LocalDate.parse("2024-06-06"),
                intervieweeRoleCode = "WITNESS",
                createdAt = LocalDateTime.parse("2024-04-04T15:12:32.004620"),
                createdBy = "AA_ADM",
                createdByDisplayName = "ADAM SMITH",
                interviewText = "Saw a pipe in his hand",
                lastModifiedAt = LocalDateTime.parse("2024-08-12T11:32:15"),
                lastModifiedBy = "JOHN_SMITH",
                lastModifiedByDisplayName = "John Smith",
              ),
            ),
            staffInvolved = "some people",
            evidenceSecured = "A piece of pipe",
            occurrenceReason = "bad behaviour",
            personsUsualBehaviour = "Good person",
            personsTrigger = "missed meal",
            protectiveFactors = "ensure taken to canteen",
          ),
          decisionAndActions =
          SyncDecisionAndActionsRequest(
            outcomeTypeCode = "OPE",
            actions = setOf(
              SyncDecisionAndActionsRequest.Actions.NON_ASSOCIATIONS_UPDATED,
              SyncDecisionAndActionsRequest.Actions.OBSERVATION_BOOK,
              SyncDecisionAndActionsRequest.Actions.SERVICE_REFERRAL,
            ),
            conclusion = "Offender needs help",
            signedOffByRoleCode = "CUSTMAN",
            date = LocalDate.parse("2024-04-08"),
            recordedBy = "FRED_ADM",
            recordedByDisplayName = "Fred Admin",
            nextSteps = "try harder",
            actionOther = "Some other info here",
          ),
        ),
        plan = SyncPlanRequest(
          caseManager = "C Jones",
          reasonForPlan = "helper",
          firstCaseReviewDate = LocalDate.parse("2024-04-15"),
          identifiedNeeds = listOf(
            SyncNeedRequest(
              id = null,
              legacyId = 65,
              identifiedNeed = "they need help",
              responsiblePerson = "Jason", intervention = "dd",
              progression = "there was some improvement",
              targetDate = LocalDate.parse("2024-08-20"),
              closedDate = LocalDate.parse("2024-04-17"),
              createdDate = LocalDate.parse("2024-04-16"),

              createdAt = LocalDateTime.parse("2024-03-16T11:32:15"),
              createdBy = "JSMITH",
              createdByDisplayName = "SOME_ONE",
              lastModifiedAt = LocalDateTime.parse("2024-04-01T11:32:15"),
              lastModifiedBy = "SOMEBODY_ELSE",
              lastModifiedByDisplayName = "Somebody else",
            ),
          ),
          reviews = listOf(
            SyncReviewRequest(
              legacyId = 675,
              id = UUID.randomUUID(),
              reviewDate = null,
              nextReviewDate = null,
              csipClosedDate = null,
              summary = null,
              recordedBy = "REC_ORDERED",
              recordedByDisplayName = "Rec Ordered",

              createdAt = LocalDateTime.parse("2024-03-16T11:32:15"),
              createdBy = "JSMITH",
              createdByDisplayName = "SOME_ONE",
              lastModifiedAt = LocalDateTime.parse("2024-04-01T11:32:15"),
              lastModifiedBy = "SOMEBODY_ELSE",
              lastModifiedByDisplayName = "Somebody else",

              actions = setOf(SyncReviewRequest.Actions.REMAIN_ON_CSIP),
              attendees = listOf(
                SyncAttendeeRequest(
                  legacyId = 432,
                  id = null,
                  name = "Anne Attendee",
                  role = "WITNESS",
                  isAttended = true,
                  contribution = "Saw a pipe in his hand",

                  createdAt = LocalDateTime.parse("2024-03-16T11:32:15"),
                  createdBy = "JSMITH",
                  createdByDisplayName = "SOME_ONE",
                  lastModifiedAt = LocalDateTime.parse("2024-04-01T11:32:15"),
                  lastModifiedBy = "SOMEBODY_ELSE",
                  lastModifiedByDisplayName = "Somebody else",
                ),
              ),
            ),
          ),
        ),
      )

    private fun dpsCSIPReportMapping(dpsCSIPReportId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") =
      ResponseMapping(
        ResponseMapping.Component.RECORD,
        id = 1234L,
        uuid = UUID.fromString(dpsCSIPReportId),
      )

    fun dpsCsipReportSyncResponse(dpsCSIPReportId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") =
      SyncResponse(mappings = setOf(dpsCSIPReportMapping(dpsCSIPReportId)))

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
        isReferralComplete = true,
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
      status = CsipRecord.Status.CSIP_OPEN,
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
      UpsertInvestigationRequest(
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
      UpsertDecisionAndActionsRequest(
        outcomeTypeCode = "CUR",
        conclusion = null,
        signedOffByRoleCode = "CUSTMAN",
        recordedBy = "FRED_ADM",
        recordedByDisplayName = "Fred Admin",
        date = LocalDate.parse("2024-04-08"),
        nextSteps = null,
        actions =
        setOf(
          UpsertDecisionAndActionsRequest.Actions.NON_ASSOCIATIONS_UPDATED,
          UpsertDecisionAndActionsRequest.Actions.OBSERVATION_BOOK,
          UpsertDecisionAndActionsRequest.Actions.SERVICE_REFERRAL,
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
          DecisionAndActions.Actions.NON_ASSOCIATIONS_UPDATED,
          DecisionAndActions.Actions.OBSERVATION_BOOK,
          DecisionAndActions.Actions.SERVICE_REFERRAL,
        ),

        conclusion = null,
        recordedBy = "FRED_ADM",
        recordedByDisplayName = "Fred Admin",
        date = LocalDate.parse("2024-04-08"),
        nextSteps = null,
        actionOther = "Some other info here",
      )

    fun dpsUpdatePlanRequest() =
      UpsertPlanRequest(
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
    fun dpsSyncCSIPPlanRequest() =
      SyncPlanRequest(
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

  fun stubSyncCSIPReport(dpsCSIPId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") {
    stubFor(
      put("/sync/csip-records").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(dpsCsipReportSyncResponse(dpsCSIPReportId = dpsCSIPId)),
      ),
    )
  }

  fun stubMigrateCSIPReportForOffender(dpsCSIPId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5", offenderNo: String = "A1234BC") {
    stubFor(
      post("/migrate/prisoners/$offenderNo/csip-records").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(dpsCSIPReport(dpsCSIPReportId = dpsCSIPId)),
      ),
    )
  }

  fun stubInsertCSIPReport(dpsCSIPId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5", offenderNo: String = "A1234BC") {
    stubFor(
      post("/prisoners/$offenderNo/csip-records").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(dpsCSIPReport(dpsCSIPReportId = dpsCSIPId)),
      ),
    )
  }

  fun stubUpdateCSIPReport(dpsCSIPId: String) {
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
  fun stubInsertCSIPSCS(dpsCSIPId: String) {
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
  fun stubInsertCSIPFactor(dpsCSIPReportId: String, dpsCSIPFactorId: String) {
    stubFor(
      post("/csip-records/$dpsCSIPReportId/referral/contributory-factors").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(dpsCSIPFactor(dpsCSIPFactorId)),
      ),
    )
  }
  fun stubUpdateCSIPFactor(dpsCSIPFactorId: String) {
    stubFor(
      patch("/csip-records/referral/contributory-factors/$dpsCSIPFactorId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(dpsCSIPFactor(dpsCSIPFactorId)),
      ),
    )
  }
  fun stubDeleteCSIPFactor(dpsCSIPFactorId: String) {
    stubDelete("/csip-records/referral/contributory-factors/$dpsCSIPFactorId")
  }

  fun stubDeleteCSIPFactorNotFound(status: HttpStatus = HttpStatus.NOT_FOUND) {
    stubDeleteErrorResponse(status = status, url = "/csip-records/referral/contributory-factors/\\S+")
  }

  // /////////////// CSIP Interview
  fun stubDeleteCSIPInterview(dpsCSIPInterviewId: String) {
    stubDelete("/csip-records/referral/investigation/interviews/$dpsCSIPInterviewId")
  }

  fun stubDeleteCSIPInterviewNotFound(status: HttpStatus = HttpStatus.NOT_FOUND) {
    stubDeleteErrorResponse(status = status, url = "/csip-records/referral/investigation/interviews/\\S+")
  }

  // /////////////// CSIP Attendee
  fun stubDeleteCSIPAttendee(dpsCSIPAttendeeId: String) {
    stubDelete("/csip-records/plan/reviews/attendees/$dpsCSIPAttendeeId")
  }

  fun stubDeleteCSIPAttendeeNotFound(status: HttpStatus = HttpStatus.NOT_FOUND) {
    stubDeleteErrorResponse(status = status, url = "/csip-records/plan/reviews/attendees/\\S+")
  }

  // /////////////// CSIP Plan
  fun stubDeleteCSIPPlan(dpsCSIPPlanId: String) {
    stubDelete("/csip-records/plan/identified-needs/$dpsCSIPPlanId")
  }

  fun stubDeleteCSIPPlanNotFound(status: HttpStatus = HttpStatus.NOT_FOUND) {
    stubDeleteErrorResponse(status = status, url = "/csip-records/plan/identified-needs/\\S+")
  }

  // /////////////// CSIP Investigation
  fun stubUpdateCSIPInvestigation(dpsCSIPId: String) {
    stubFor(
      put("/csip-records/$dpsCSIPId/referral/investigation").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(OK.value())
          .withBody(dpsCSIPInvestigation()),
      ),
    )
  }

  fun stubUpdateCSIPDecision(dpsCSIPId: String) {
    stubFor(
      put("/csip-records/$dpsCSIPId/referral/decision-and-actions").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(OK.value())
          .withBody(dpsCSIPDecision()),
      ),
    )
  }

  fun stubUpdateCSIPPlan(dpsCSIPId: String) {
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

  fun syncCSIPCount() =
    findAll(putRequestedFor(urlMatching("/sync/csip-records"))).count()

  fun createCSIPMigrationCount() =
    findAll(postRequestedFor(urlMatching("/migrate/prisoners/.*"))).count()

  fun createCSIPSyncCount() =
    findAll(postRequestedFor(urlMatching("/prisoners/.*"))).count()

  fun createCSIPFactorSyncCount() =
    findAll(postRequestedFor(urlMatching("/csip-records/.*/referral/contributory-factors"))).count()

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder =
    this.withBody(objectMapper.writeValueAsString(body))
}
