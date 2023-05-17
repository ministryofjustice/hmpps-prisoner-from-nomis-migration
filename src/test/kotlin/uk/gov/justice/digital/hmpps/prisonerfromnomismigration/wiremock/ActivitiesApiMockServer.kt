package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments.sampleAppointment

class ActivitiesApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val activitiesApi = ActivitiesApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    activitiesApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    activitiesApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    activitiesApi.stop()
  }
}

class ActivitiesApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8086
    private val objectMapper = jacksonObjectMapper().apply {
      findAndRegisterModules()
    }
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

  fun stubCreateAppointmentForMigration(appointmentInstanceId: Long) {
    val response = objectMapper.writeValueAsString(sampleAppointment(appointmentInstanceId))

    stubFor(
      post(urlMatching("/migrate-appointment"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(response),
        ),
    )
  }

  fun verifyCreatedDate(createdValue: String, updatedValue: String) =
    verify(
      postRequestedFor(urlPathEqualTo("/migrate-appointment"))
        .withRequestBody(matchingJsonPath("created", equalTo(createdValue)))
        .withRequestBody(matchingJsonPath("updated", equalTo(updatedValue))),
    )

  fun createAppointmentCount() =
    findAll(postRequestedFor(urlMatching("/migrate-appointment"))).count()
}
