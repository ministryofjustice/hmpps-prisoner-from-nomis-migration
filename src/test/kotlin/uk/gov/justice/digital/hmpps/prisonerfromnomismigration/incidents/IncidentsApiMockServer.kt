package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.CorrectionRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.Event
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.HistoricalQuestion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.HistoricalResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.History
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisSyncReportId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.PrisonerInvolvement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.Question
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.ReportBasic
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.ReportWithDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.Response
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.SimplePageReportBasic
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.StaffInvolvement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.StatusHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.min

class IncidentsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val incidentsApi = IncidentsApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    incidentsApi.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    incidentsApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    incidentsApi.stop()
  }
}

class IncidentsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8089
    private const val DPS_INCIDENT_ID = "fb4b2e91-91e7-457b-aa17-797f8c5c2f42"

    fun dpsIncidentReportId(dpsIncidentId: String) = NomisSyncReportId(id = UUID.fromString(dpsIncidentId))

    fun dpsIncidentReport(nomisIncidentId: String = "1234") = ReportWithDetails(
      id = UUID.randomUUID(),
      reportReference = nomisIncidentId,
      type = ReportWithDetails.Type.ATTEMPTED_ESCAPE_FROM_ESCORT,
      incidentDateAndTime = LocalDateTime.parse("2021-07-05T10:35:17"),
      prisonId = "ASI",
      location = "ASI",
      title = "There was an incident in the exercise yard",
      description = "Fred and Jimmy were fighting outside.",
      nomisType = "ATT_ESC_E",
      nomisStatus = "AWAN",
      event = Event(
        id = UUID.randomUUID(),
        eventReference = nomisIncidentId,
        eventDateAndTime = LocalDateTime.parse("2021-07-05T10:35:17"),
        prisonId = "ASI",
        location = "ASI",
        title = "There was a problem",
        description = "Fighting was happening",
        createdAt = LocalDateTime.parse("2021-07-23T10:35:17"),
        modifiedAt = LocalDateTime.parse("2021-07-23T10:35:17"),
        modifiedBy = "JSMITH",
      ),
      reportedBy = "FSTAFF_GEN",
      reportedAt = LocalDateTime.parse("2021-07-07T10:35:17"),
      status = ReportWithDetails.Status.DRAFT,
      assignedTo = "BJONES",
      questions = listOf(
        Question(
          code = "1234",
          question = "Was anybody hurt?",
          additionalInformation = null,
          sequence = 1,
          responses = listOf(
            Response(
              response = "Yes",
              recordedBy = "JSMITH",
              recordedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
              additionalInformation = null,
              sequence = 1,
            ),
          ),
        ),
      ),
      history = listOf(
        History(
          type = History.Type.ABSCONDER,
          changedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
          changedBy = "JSMITH",
          questions = listOf(
            HistoricalQuestion(
              code = "HQ1",
              question = "Were tools involved?",
              responses = listOf(
                HistoricalResponse(
                  response = "Yes",
                  sequence = 1,
                  recordedBy = "Fred Jones",
                  recordedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
                  additionalInformation = "more info",
                ),
              ),
              additionalInformation = "some info",
              sequence = 1,
            ),
          ),
        ),
      ),
      historyOfStatuses = listOf(
        StatusHistory(
          status = StatusHistory.Status.DRAFT,
          changedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
          changedBy = "JSMITH",
        ),
      ),
      staffInvolved = listOf(
        StaffInvolvement(
          sequence = 1,
          staffUsername = "Dave Jones",
          staffRole = StaffInvolvement.StaffRole.ACTIVELY_INVOLVED,
          comment = "Dave was hit",
          firstName = "Dave",
          lastName = "Jones",
        ),
      ),
      prisonersInvolved = listOf(
        PrisonerInvolvement(
          sequence = 1,
          prisonerNumber = "A1234BC",
          prisonerRole = PrisonerInvolvement.PrisonerRole.ABSCONDER,
          outcome = PrisonerInvolvement.Outcome.PLACED_ON_REPORT,
          comment = "There were issues",
          firstName = "Dave",
          lastName = "Jones",
        ),
        PrisonerInvolvement(
          sequence = 2,
          prisonerNumber = "A1234BC",
          prisonerRole = PrisonerInvolvement.PrisonerRole.ABSCONDER,
          outcome = PrisonerInvolvement.Outcome.PLACED_ON_REPORT,
          firstName = "Dave",
          lastName = "Jones",
        ),
      ),
      correctionRequests = listOf(
        CorrectionRequest(
          sequence = 1,
          descriptionOfChange = "There was a change",
          correctionRequestedBy = "Fred Black",
          correctionRequestedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
        ),
      ),
      createdAt = LocalDateTime.parse("2021-07-05T10:35:17"),
      modifiedAt = LocalDateTime.parse("2021-07-15T10:35:17"),
      modifiedBy = "JSMITH",
      createdInNomis = true,
      lastModifiedInNomis = true,
      staffInvolvementDone = true,
      prisonerInvolvementDone = true,
    )

    fun dpsBasicIncidentReport(dpsIncidentId: String = DPS_INCIDENT_ID, prisonId: String = "ASI") = ReportBasic(
      id = UUID.fromString(dpsIncidentId),
      reportReference = "1234",
      type = ReportBasic.Type.SELF_HARM,
      incidentDateAndTime = LocalDateTime.parse("2021-07-05T10:35:17"),
      prisonId = prisonId,
      location = prisonId,
      title = "There was an incident in the exercise yard",
      description = "Fred and Jimmy were fighting outside.",
      reportedBy = "JSMITH",
      reportedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
      status = ReportBasic.Status.DRAFT,
      assignedTo = "BJONES",
      createdAt = LocalDateTime.parse("2021-07-05T10:35:17"),
      modifiedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
      modifiedBy = "JSMITH",
      createdInNomis = true,
      lastModifiedInNomis = true,
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

  fun stubIncidentUpsert(dpsIncidentId: String = "fb4b2e91-91e7-457b-aa17-797f8c5c2f42") {
    stubFor(
      post("/sync/upsert").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(dpsIncidentReportId(dpsIncidentId)),
      ),
    )
  }

  fun stubIncidentUpsert(
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    stubFor(
      post("/sync/upsert")
        .willReturn(
          aResponse()
            .withStatus(status.value())
            .withHeader("Content-Type", APPLICATION_JSON_VALUE)
            .withBody(error),
        ),
    )
  }

  fun stubIncidentDelete(incidentId: String = "fb4b2e91-91e7-457b-aa17-797f8c5c2f42") {
    stubFor(
      delete("/incident-reports/$incidentId").willReturn(
        aResponse()
          .withStatus(NO_CONTENT.value()),
      ),
    )
  }

  fun stubIncidentDeleteNotFound(incidentId: String = "fb4b2e91-91e7-457b-aa17-797f8c5c2f42") {
    stubFor(
      delete("/incident-reports/$incidentId").willReturn(
        aResponse()
          .withStatus(NOT_FOUND.value()),
      ),
    )
  }

  fun stubGetBasicIncident() {
    stubFor(
      get(urlMatching("/incident-reports/reference/[0-9]+")).willReturn(
        aResponse()
          .withStatus(HttpStatus.OK.value())
          .withHeader("Content-Type", APPLICATION_JSON_VALUE)
          .withBody(dpsBasicIncidentReport()),
      ),
    )
  }

  fun stubGetIncident(nomisIncidentId: Long = 1234) {
    stubFor(
      get(urlMatching("/incident-reports/reference/$nomisIncidentId/with-details")).willReturn(
        aResponse()
          .withStatus(HttpStatus.OK.value())
          .withHeader("Content-Type", APPLICATION_JSON_VALUE)
          .withBody(dpsIncidentReport(nomisIncidentId.toString())),
      ),
    )
  }
  fun stubGetIncidents(startIncidentId: Long, endIncidentId: Long) {
    (startIncidentId..endIncidentId).forEach { nomisIncidentId ->
      stubGetIncident(nomisIncidentId)
    }
  }

  fun stubGetIncidentCounts(totalElements: Long = 3, pageSize: Long = 20) {
    val content: List<ReportBasic> = (1..min(pageSize, totalElements)).map {
      dpsBasicIncidentReport(dpsIncidentId = UUID.randomUUID().toString())
    }
    stubFor(
      get(urlPathMatching("/incident-reports"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              SimplePageReportBasic(
                content = content,
                number = 0,
                propertySize = pageSize.toInt(),
                totalElements = totalElements,
                sort = listOf("incidentDateAndTime,DESC"),
                numberOfElements = pageSize.toInt(),
                totalPages = totalElements.toInt(),
              ),
            ),
        ),
    )
  }

  fun stubGetIncidentsWithError(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    stubFor(
      get(urlPathMatching("/incident-reports"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(error),
        ),
    )
  }

  fun verifyMigrationGetBasicIncident() = verify(getRequestedFor(urlMatching("/incident-reports/reference/[0-9]+")))

  fun verifyGetIncidentCounts(times: Int = 1) = verify(exactly(times), getRequestedFor(urlPathMatching("/incident-reports")))

  fun verifyGetIncidentDetail(times: Int = 1) = verify(exactly(times), getRequestedFor(urlMatching("/incident-reports/reference/[0-9]+/with-details")))

  fun createIncidentUpsertCount() = findAll(postRequestedFor(urlEqualTo("/sync/upsert"))).count()

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder = this.withBody(objectMapper.writeValueAsString(body))
}
