package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.NomisAlertMapping
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.NomisAlertMapping.Status.CREATED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.NomisAlertMapping.Status.UPDATED
import java.util.UUID

class AlertsDpsApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val dpsAlertsServer = AlertsDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsAlertsServer.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    dpsAlertsServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsAlertsServer.stop()
  }
}

class AlertsDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8092
  }

  fun stubPostAlertForCreate(
    response: NomisAlertMapping = NomisAlertMapping(
      offenderBookId = 12345,
      alertSeq = 2,
      alertUuid = UUID.randomUUID(),
      status = CREATED,
    ),
  ) {
    stubFor(
      post("/nomis-alerts")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(AlertsDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubPostAlertForUpdate(
    response: NomisAlertMapping = NomisAlertMapping(
      offenderBookId = 12345,
      alertSeq = 2,
      alertUuid = UUID.randomUUID(),
      status = UPDATED,
    ),
  ) {
    stubFor(
      post("/nomis-alerts")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(AlertsDpsApiExtension.objectMapper.writeValueAsString(response)),
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
}
