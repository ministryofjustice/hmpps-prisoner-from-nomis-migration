package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.Evidence
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.HistoricalQuestion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.HistoricalResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.History
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.Location
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisSyncReportId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.PrisonerInvolvement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.Question
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.ReportBasic
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.ReportWithDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.Response
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.StaffInvolvement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.StatusHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import java.util.UUID

class IncidentsApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
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

    fun dpsIncidentReportId(dpsIncidentId: String) =
      NomisSyncReportId(id = UUID.fromString(dpsIncidentId))

    fun dpsIncidentReport(dpsIncidentId: String = DPS_INCIDENT_ID) =
      ReportWithDetails(
        id = UUID.fromString(dpsIncidentId),
        incidentNumber = "1234",
        type = ReportWithDetails.Type.SELF_HARM,
        incidentDateAndTime = "2021-07-05T10:35:17",
        prisonId = "MDI",
        title = "There was an incident in the exercise yard",
        description = "Fred and Jimmy were fighting outside.",
        event = Event(
          eventId = "123",
          eventDateAndTime = "2021-07-05T10:35:17",
          prisonId = "MDI",
          title = "There was a problem",
          description = "Fighting was happening",
          createdAt = "2021-07-05T10:35:17",
          modifiedAt = "2021-07-05T10:35:17",
          modifiedBy = "JSMITH",
        ),
        reportedBy = "JSMITH",
        reportedAt = "2021-07-05T10:35:17",
        status = ReportWithDetails.Status.DRAFT,
        assignedTo = "BJONES",
        questions = listOf(
          Question(
            code = "string",
            question = "string",
            responses = listOf(
              Response(
                response = "They answered",
                recordedBy = "JSMITH",
                recordedAt = "2021-07-05T10:35:17",
                additionalInformation = null,
              ),
            ),
            additionalInformation = null,
          ),
        ),
        history = listOf(
          History(
            type = History.Type.ABSCONDER,
            changedAt = "2021-07-05T10:35:17",
            changedBy = "JSMITH",
            questions = listOf(
              HistoricalQuestion(
                code = "string",
                question = "string",
                responses = listOf(
                  HistoricalResponse(
                    response = "string",
                    recordedBy = "string",
                    recordedAt = "2021-07-05T10:35:17",
                    additionalInformation = "more info",
                  ),
                ),
                additionalInformation = "some info",
              ),
            ),
          ),
        ),
        historyOfStatuses = listOf(
          StatusHistory(
            status = StatusHistory.Status.DRAFT,
            changedAt = "2021-07-05T10:35:17",
            changedBy = "JSMITH",
          ),
        ),
        staffInvolved = listOf(
          StaffInvolvement(
            staffUsername = "string",
            staffRole = StaffInvolvement.StaffRole.ACTIVELY_INVOLVED,
            comment = "null",
          ),
        ),
        prisonersInvolved = listOf(
          PrisonerInvolvement(
            prisonerNumber = "string",
            prisonerRole = PrisonerInvolvement.PrisonerRole.ABSCONDER,
            outcome = null,
            comment = "null",
          ),
        ),
        locations = listOf(
          Location(
            locationId = "string",
            type = "string",
            description = "string",
          ),
        ),
        evidence = listOf(Evidence(type = "string", description = "string")),

        correctionRequests = listOf(
          CorrectionRequest(
            reason = CorrectionRequest.Reason.MISTAKE,
            descriptionOfChange = "string",
            correctionRequestedBy = "string",
            correctionRequestedAt = "2021-07-05T10:35:17",
          ),
        ),
        createdAt = "2021-07-05T10:35:17",
        modifiedAt = "2021-07-05T10:35:17",
        modifiedBy = "JSMITH",
        createdInNomis = true,
      )

    fun dpsBasicIncidentReport(dpsIncidentId: String = DPS_INCIDENT_ID) =
      ReportBasic(
        id = UUID.fromString(dpsIncidentId),
        incidentNumber = "1234",
        type = ReportBasic.Type.SELF_HARM,
        incidentDateAndTime = "2021-07-05T10:35:17",
        prisonId = "MDI",
        title = "There was an incident in the exercise yard",
        description = "Fred and Jimmy were fighting outside.",
        reportedBy = "JSMITH",
        reportedAt = "2021-07-05T10:35:17",
        status = ReportBasic.Status.DRAFT,
        assignedTo = "BJONES",
        createdAt = "2021-07-05T10:35:17",
        modifiedAt = "2021-07-05T10:35:17",
        modifiedBy = "JSMITH",
        createdInNomis = true,
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
          .withBody(
            objectMapper.writeValueAsString(dpsIncidentReportId(dpsIncidentId)),
          ),
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
            .withBody(objectMapper.writeValueAsString(error)),
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
      get(WireMock.urlPathMatching("/incident-reports/incident-number/.*")).willReturn(
        aResponse()
          .withStatus(HttpStatus.OK.value())
          .withHeader("Content-Type", APPLICATION_JSON_VALUE)
          .withBody(
            objectMapper.writeValueAsString(dpsBasicIncidentReport()),
          ),
      ),
    )
  }

  fun verifyGetBasicIncident() =
    verify(
      getRequestedFor(urlMatching("/incident-reports/incident-number/.*")),
    )

  fun createIncidentUpsertCount() =
    findAll(postRequestedFor(urlEqualTo("/sync/upsert"))).count()
}
