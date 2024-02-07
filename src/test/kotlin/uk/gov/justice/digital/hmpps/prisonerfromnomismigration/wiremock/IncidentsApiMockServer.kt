package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus.CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentMigrateResponse

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

  fun stubIncidentForMigration(incidentId: Long = 1234) {
    stubFor(
      post(urlMatching("/incidents/migrate")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(
            IncidentMigrateResponse(
              incidentId = incidentId,
            ).toJson(),
          ),
      ),
    )
  }

  fun createIncidentMigrationCount() =
    findAll(postRequestedFor(urlMatching("/incidents/migrate"))).count()
}

private fun Any.toJson(): String = ObjectMapper().writeValueAsString(this)
