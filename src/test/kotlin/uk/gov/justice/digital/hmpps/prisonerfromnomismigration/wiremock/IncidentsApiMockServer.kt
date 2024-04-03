package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.EventDetail
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.IncidentReport
import java.util.UUID

class IncidentsApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val incidentsApi = IncidentsApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    incidentsApi.start()
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

  fun stubIncidentUpsert(incidentId: String = "fb4b2e91-91e7-457b-aa17-797f8c5c2f42") {
    stubFor(
      post("/sync/upsert").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(
            IncidentReport(
              id = UUID.fromString(incidentId),
              incidentNumber = "1234",
              incidentType = IncidentReport.IncidentType.SELF_HARM,
              incidentDateAndTime = "2021-07-05T10:35:17",
              prisonId = "LEI",
              summary = "There was a fight",
              incidentDetails = "Prisoner Smith hit Prisoner Jones",
              event = EventDetail(
                eventId = "123",
                eventDateAndTime = "2021-07-05T10:35:17",
                prisonId = "LEI",
                summary = "There was a problem",
                eventDetails = "Fighting was happening",
              ),
              reportedBy = "Jim Smith",
              reportedDate = "2021-07-05T10:35:17",
              status = IncidentReport.Status.DRAFT,
              assignedTo = "string",
              createdDate = "2021-07-05T10:35:17",
              lastModifiedDate = "2021-07-05T10:35:17",
              lastModifiedBy = "string",
              createdInNomis = true,
            ).toJson(),
          ),
      ),
    )
  }

  fun stubIncidentDelete(incidentId: String = "fb4b2e91-91e7-457b-aa17-797f8c5c2f42") {
    stubFor(
      delete("/sync/upsert/$incidentId").willReturn(
        aResponse()
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubIncidentDeleteNotFound(incidentId: String = "fb4b2e91-91e7-457b-aa17-797f8c5c2f42") {
    stubFor(
      delete("/sync/upsert/$incidentId").willReturn(
        aResponse()
          .withStatus(HttpStatus.NOT_FOUND.value()),
      ),
    )
  }

  fun createIncidentUpsertCount() =
    findAll(postRequestedFor(urlMatching("/sync/upsert"))).count()
}

private fun Any.toJson(): String = ObjectMapper().writeValueAsString(this)
