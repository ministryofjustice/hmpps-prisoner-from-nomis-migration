package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus

class ActivitiesApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val activitiesApi = ActivitiesApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    activitiesApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    activitiesApi.resetRequests()
    activitiesApi.resetScenarios()
  }

  override fun afterAll(context: ExtensionContext) {
    activitiesApi.stop()
  }
}

class ActivitiesApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8086
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

  fun stubCreateAppointmentForMigration(appointmentInstanceIds: LongProgression) {
    var state = STARTED
    var count = 1
    appointmentInstanceIds.forEach { appointmentInstanceId ->
      stubFor(
        post(WireMock.urlMatching("/migrate-appointment"))
          .inScenario("Appointment creation")
          .whenScenarioStateIs(state)
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.CREATED.value())
              .withBody("""{"id": "$appointmentInstanceId"}"""),
          )
          .willSetStateTo(count.toString()),
      )
      state = count.toString()
      count++
    }
  }

  fun createAppointmentCount() =
    findAll(WireMock.postRequestedFor(WireMock.urlMatching("/migrate-appointment"))).count()
}
