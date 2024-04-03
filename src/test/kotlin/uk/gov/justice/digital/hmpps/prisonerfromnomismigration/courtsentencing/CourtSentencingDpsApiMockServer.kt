package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtCaseResponse
import java.util.UUID

class CourtSentencingDpsApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val dpsCourtSentencingServer = CourtSentencingDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsCourtSentencingServer.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    dpsCourtSentencingServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsCourtSentencingServer.stop()
  }
}

class CourtSentencingDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8094
  }

  fun stubPostCourtCaseForCreate(
    courtCaseId: String = UUID.randomUUID().toString(),
    response: CreateCourtCaseResponse = CreateCourtCaseResponse(
      courtCaseUuid = courtCaseId,
    ),
  ) {
    stubFor(
      post("/court-case")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(CourtSentencingDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubPutCourtCaseForUpdate(
    courtCaseId: String = UUID.randomUUID().toString(),
    response: CreateCourtCaseResponse = CreateCourtCaseResponse(
      courtCaseUuid = courtCaseId,
    ),
  ) {
    stubFor(
      put("/court-case/$courtCaseId")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(CourtSentencingDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubDeleteCourtCase(
    courtCaseId: String = UUID.randomUUID().toString(),
  ) {
    stubFor(
      delete("/court-case/$courtCaseId")
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
