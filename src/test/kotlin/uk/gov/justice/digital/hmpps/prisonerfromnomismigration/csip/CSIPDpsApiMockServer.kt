package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.ResponseMapping
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
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
        prisonNumber = "A1234BC",
        prisonCodeWhenRecorded = "MDI",
        activeCaseloadId = "MDI",
        actionedAt = LocalDateTime.parse("2024-04-01T10:32:12.867081"),
        actionedBy = "JSMITH",
        actionedByDisplayName = "JSMITH",
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
              createdBy = "CFACTOR",
              createdByDisplayName = "CFACTOR",
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
              createdBy = "PPLAN",
              createdByDisplayName = "Peter Plan",
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
              createdBy = "FJAMES",
              createdByDisplayName = "FRED JAMES",
              lastModifiedAt = null,
              lastModifiedBy = null,
              lastModifiedByDisplayName = null,

              actions = setOf(
                SyncReviewRequest.Actions.REMAIN_ON_CSIP,
                SyncReviewRequest.Actions.CLOSE_CSIP,
              ),
              attendees = listOf(
                SyncAttendeeRequest(
                  legacyId = 221,
                  name = "same jones",
                  role = "person",
                  isAttended = true,
                  contribution = "talked about things",
                  createdAt = LocalDateTime.parse("2024-08-20T10:33:48.946787"),
                  createdBy = "DBULL_ADM",
                  createdByDisplayName = "DOM BULL",
                ),
              ),
            ),
          ),
        ),
      )

    fun dpsSyncCsipRequestMinimal() =
      SyncCsipRequest(
        legacyId = 1234,
        logCode = "ASI-001",
        prisonNumber = "A1234BC",
        prisonCodeWhenRecorded = "MDI",
        activeCaseloadId = "MDI",
        actionedAt = LocalDateTime.parse("2024-04-01T10:32:12.867081"),
        actionedBy = "JSMITH",
        actionedByDisplayName = "JSMITH",
        createdAt = LocalDateTime.parse("2024-04-01T10:32:12.867081"),
        createdBy = "JSMITH",
        createdByDisplayName = "JSMITH",
        referral = SyncReferralRequest(
          incidentDate = LocalDate.parse("2024-06-12"),
          incidentTypeCode = "INT",
          incidentLocationCode = "LIB",
          referredBy = "JIM_ADM",
          referralDate = LocalDate.parse("2024-04-04"),
          refererAreaCode = "EDU",
          isSaferCustodyTeamInformed = SyncReferralRequest.IsSaferCustodyTeamInformed.NO,
          contributoryFactors = listOf(),
          isProactiveReferral = false,
          isStaffAssaulted = false,
          isReferralComplete = false,
        ),
      )

    private fun dpsCSIPReportResponseMapping(dpsCSIPReportId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") =
      ResponseMapping(
        ResponseMapping.Component.RECORD,
        id = 1234L,
        uuid = UUID.fromString(dpsCSIPReportId),
      )

    private fun dpsCSIPFactorResponseMapping(dpsCSIPFactorId: String) =
      ResponseMapping(
        ResponseMapping.Component.CONTRIBUTORY_FACTOR,
        id = 1234L,
        uuid = UUID.fromString(dpsCSIPFactorId),
      )

    fun dpsCsipReportSyncResponse(dpsCSIPReportId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") =
      SyncResponse(mappings = setOf(dpsCSIPReportResponseMapping(dpsCSIPReportId)))

    fun dpsCsipReportSyncResponseWithFactor(dpsCSIPFactorId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e6") =
      SyncResponse(
        mappings = setOf(
          dpsCSIPReportResponseMapping(),
          dpsCSIPFactorResponseMapping(dpsCSIPFactorId),
        ),
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

  fun stubSyncCSIPReportNoMappingUpdates() {
    stubFor(
      put("/sync/csip-records").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(OK.value())
          .withBody(SyncResponse(setOf())),
      ),
    )
  }
  fun stubSyncCSIPReportAttendeeMappingUpdate(dpsCSIPAttendeeId: String, nomisCSIPAttendeeId: Long) {
    stubSyncCSIPReport(
      ResponseMapping(
        ResponseMapping.Component.ATTENDEE,
        id = nomisCSIPAttendeeId,
        uuid = UUID.fromString(dpsCSIPAttendeeId),
      ),
    )
  }
  fun stubSyncCSIPReportFactorMappingUpdate(dpsCSIPFactorId: String, nomisCSIPFactorId: Long) {
    stubSyncCSIPReport(
      ResponseMapping(
        ResponseMapping.Component.CONTRIBUTORY_FACTOR,
        id = nomisCSIPFactorId,
        uuid = UUID.fromString(dpsCSIPFactorId),
      ),
    )
  }
  fun stubSyncCSIPReportInterviewMappingUpdate(dpsCSIPInterviewId: String, nomisCSIPInterviewId: Long) {
    stubSyncCSIPReport(
      ResponseMapping(
        ResponseMapping.Component.INTERVIEW,
        id = nomisCSIPInterviewId,
        uuid = UUID.fromString(dpsCSIPInterviewId),
      ),
    )
  }
  fun stubSyncCSIPReportPlanMappingUpdate(dpsCSIPPlanId: String, nomisCSIPPlanId: Long) {
    stubSyncCSIPReport(
      ResponseMapping(
        ResponseMapping.Component.IDENTIFIED_NEED,
        id = nomisCSIPPlanId,
        uuid = UUID.fromString(dpsCSIPPlanId),
      ),
    )
  }
  fun stubSyncCSIPReportReviewMappingUpdate(dpsCSIPReviewId: String, nomisCSIPReviewId: Long) {
    stubSyncCSIPReport(
      ResponseMapping(
        ResponseMapping.Component.REVIEW,
        id = nomisCSIPReviewId,
        uuid = UUID.fromString(dpsCSIPReviewId),
      ),
    )
  }
  private fun stubSyncCSIPReport(responseMapping: ResponseMapping) {
    stubFor(
      put("/sync/csip-records").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(OK.value())
          .withBody(SyncResponse(setOf(responseMapping))),
      ),
    )
  }

  fun stubSyncCSIPReportWithFactor(dpsCSIPFactorId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e6") {
    stubFor(
      put("/sync/csip-records").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(dpsCsipReportSyncResponseWithFactor(dpsCSIPFactorId = dpsCSIPFactorId)),
      ),
    )
  }

  fun stubSyncCSIPReport(status: HttpStatus) = stubPutErrorResponse(
    status = status,
    url = "/sync/csip-records",
    error =
    ErrorResponse(status.value(), userMessage = "There was an Error"),
  )

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

  private fun stubDeleteErrorResponse(status: HttpStatus, url: String, error: ErrorResponse = ErrorResponse(status = status.value())) {
    stubFor(
      delete(urlPathMatching(url))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  private fun stubPutErrorResponse(status: HttpStatus, url: String, error: ErrorResponse = ErrorResponse(status = status.value())) {
    stubFor(
      put(urlPathMatching(url))
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

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder =
    this.withBody(objectMapper.writeValueAsString(body))
}
