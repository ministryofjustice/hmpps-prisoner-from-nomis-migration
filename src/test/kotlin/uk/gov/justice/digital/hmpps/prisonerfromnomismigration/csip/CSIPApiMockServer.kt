package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.CREATED
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateCsipRecordRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateReferralRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CreateSaferCustodyScreeningOutcomeRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.CsipRecord
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.ReferenceData
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.Referral
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.model.SaferCustodyScreeningOutcome
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

    fun dpsCreateCsipRecordRequest() =
      CreateCsipRecordRequest(
        logNumber = "ASI-001",
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
          incidentTime = "10:00",
          referralSummary = null,
          isProactiveReferral = true,
          isStaffAssaulted = true,
          assaultedStaffName = "Fred Jones",
        ),
      )

    fun dpsCSIPReport(dpsCSIPReportId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") = CsipRecord(
      recordUuid = UUID.fromString(dpsCSIPReportId),
      prisonNumber = "1234",
      createdAt = LocalDateTime.parse("2024-03-29T11:32:15"),
      createdBy = "JIM_SMITH",
      createdByDisplayName = "Jim Smith",
      referral =
      Referral(
        incidentDate = LocalDate.parse("2024-03-27"),
        incidentType = ReferenceData(
          code = "incidentTypeCode",
          createdAt = LocalDateTime.parse("2024-03-29T11:32:16"),
          createdBy = "JIM_SMITH",
        ),
        incidentLocation = ReferenceData(
          code = "incidentLocationCode",
          createdAt = LocalDateTime.parse("2024-03-29T11:32:16"),
          createdBy = "JIM_SMITH",
        ),
        referredBy = "Jim Smith",
        refererArea = ReferenceData(
          code = "EDU",
          createdAt = LocalDateTime.parse("2024-03-29T11:32:16"),
          createdBy = "JIM_SMITH",
        ),
        incidentInvolvement = ReferenceData(
          code = "involvementCode",
          createdAt = LocalDateTime.parse("2024-03-29T11:32:16"),
          createdBy = "JIM_SMITH",
        ),
        descriptionOfConcern = "Needs guidance",
        knownReasons = "Fighting",
        contributoryFactors = listOf(),
        incidentTime = null,
        referralSummary = null,
        isProactiveReferral = null,
        isStaffAssaulted = null,
        assaultedStaffName = null,
        otherInformation = null,
        isSaferCustodyTeamInformed = null,
        isReferralComplete = null,
        investigation = null,
        saferCustodyScreeningOutcome = null,
        decisionAndActions = null,
      ),
      prisonCodeWhenRecorded = null,
      logNumber = null,
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
      )

    fun dpsSaferCustodyScreening() =
      SaferCustodyScreeningOutcome(
        outcome = ReferenceData(
          code = "CUR",
          description	= "Progress to CSIP",
          listSequence = 1,
          createdAt = LocalDateTime.parse("2024-03-29T11:32:16"),
          createdBy = "FRED_ADM",
        ),
        recordedBy = "FRED_ADM",
        recordedByDisplayName = "FRED_ADM",
        date = LocalDate.parse("2024-04-08"),
        reasonForDecision = "There is a reason for the decision - it goes here",
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
          .withBody(CSIPApiExtension.objectMapper.writeValueAsString(dpsCSIPReport(dpsCSIPReportId = dpsCSIPId))),
      ),
    )
  }

  fun stubCSIPInsert(dpsCSIPId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5", offenderNo: String = "A1234BC") {
    stubFor(
      post("/prisoners/$offenderNo/csip-records").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(CSIPApiExtension.objectMapper.writeValueAsString(dpsCSIPReport(dpsCSIPReportId = dpsCSIPId))),
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
    stubFor(
      delete(WireMock.urlPathMatching("/csip-records/.*"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value()),
        ),
    )
  }

  fun stubCSIPInsertSCS(dpsCSIPId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") {
    stubFor(
      post("/csip-records/$dpsCSIPId/referral/safer-custody-screening").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(
            CSIPApiExtension.objectMapper.writeValueAsString(dpsSaferCustodyScreening()),
          ),
      ),
    )
  }

  fun createCSIPMigrationCount() =
    findAll(postRequestedFor(urlMatching("/migrate/prisoners/.*"))).count()

  fun createCSIPSyncCount() =
    findAll(postRequestedFor(urlMatching("/prisoners/.*"))).count()
}
