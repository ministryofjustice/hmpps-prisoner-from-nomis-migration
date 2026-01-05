package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisSyncReportId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.ReportBasic
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import java.time.LocalDateTime
import java.util.UUID

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
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jackson2ObjectMapper") as ObjectMapper)
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

    fun dpsBasicIncidentReport(dpsIncidentId: String = DPS_INCIDENT_ID, prisonId: String = "ASI") = ReportBasic(
      id = UUID.fromString(dpsIncidentId),
      reportReference = "1234",
      type = ReportBasic.Type.SELF_HARM_1,
      incidentDateAndTime = LocalDateTime.parse("2021-07-05T10:35:17"),
      location = prisonId,
      title = "There was an incident in the exercise yard",
      description = "Fred and Jimmy were fighting outside.",
      reportedBy = "JSMITH",
      reportedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
      status = ReportBasic.Status.DRAFT,
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

  fun verifyMigrationGetBasicIncident() = verify(getRequestedFor(urlMatching("/incident-reports/reference/[0-9]+")))

  fun createIncidentUpsertCount() = findAll(postRequestedFor(urlEqualTo("/sync/upsert"))).count()

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder = this.withBody(objectMapper.writeValueAsString(body))
}
