package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.Alert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.AlertCodeSummary
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.model.ResyncedAlert
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class AlertsDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsAlertsServer = AlertsDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsAlertsServer.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jackson2ObjectMapper") as ObjectMapper)
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
    fun dpsAlert() = Alert(
      alertUuid = UUID.randomUUID(),
      prisonNumber = "A1234AA",
      alertCode = AlertCodeSummary(
        alertTypeCode = "A",
        alertTypeDescription = "Alert type description",
        code = "ABC",
        description = "Alert code description",
        canBeAdministered = false,
      ),
      description = "Alert description",
      authorisedBy = "A. Nurse, An Agency",
      activeFrom = LocalDate.parse("2021-09-27"),
      activeTo = LocalDate.parse("2022-07-15"),
      isActive = true,
      createdAt = LocalDateTime.parse("2024-02-28T13:56:10"),
      createdBy = "USER1234",
      createdByDisplayName = "Firstname Lastname",
      lastModifiedAt = LocalDateTime.parse("2024-02-28T13:56:10"),
      lastModifiedBy = "USER1234",
      lastModifiedByDisplayName = "Firstname Lastname",
    )

    fun resyncedAlert() = ResyncedAlert(
      alertUuid = UUID.randomUUID(),
      alertSeq = 1,
      offenderBookId = 1234567,
    )
  }

  fun stubPostAlert(
    prisonerNumber: String,
    response: Alert = dpsAlert(),
  ) {
    stubFor(
      post("/prisoners/$prisonerNumber/alerts")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(AlertsDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubResynchroniseAlerts(
    offenderNo: String,
    response: List<ResyncedAlert> = listOf(resyncedAlert()),
  ) {
    stubFor(
      post("/resync/$offenderNo/alerts")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(AlertsDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubPutAlert(
    response: Alert = dpsAlert(),
  ) {
    stubFor(
      put(urlPathMatching("/alerts/.*"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(AlertsDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubDeleteAlert() {
    stubFor(
      delete(urlPathMatching("/alerts/.*"))
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json"),
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
