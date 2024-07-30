package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCharge
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtCaseResponse
import java.time.LocalDate
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

  fun stubPostCourtCaseForCreateMigration(
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
            .withStatus(200)
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

  fun stubPostCourtAppearanceForCreate(
    courtAppearanceId: UUID = UUID.randomUUID(),
    response: CreateCourtAppearanceResponse = CreateCourtAppearanceResponse(
      appearanceUuid = courtAppearanceId,
    ),
  ) {
    stubFor(
      post("/court-appearance")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(CourtSentencingDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubPutCourtAppearanceForUpdate(
    courtAppearanceId: UUID = UUID.randomUUID(),
    response: CreateCourtAppearanceResponse = CreateCourtAppearanceResponse(
      appearanceUuid = courtAppearanceId,
    ),
  ) {
    stubFor(
      put("/court-appearance/$courtAppearanceId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(CourtSentencingDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubDeleteCourtAppearance(
    courtAppearanceId: String = UUID.randomUUID().toString(),
  ) {
    stubFor(
      delete("/court-appearance/$courtAppearanceId")
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubPostCourtChargeForCreate(
    courtChargeId: String = UUID.randomUUID().toString(),
    courtAppearanceId: String = UUID.randomUUID().toString(),
    response: CreateNewChargeResponse = CreateNewChargeResponse(
      chargeUuid = UUID.fromString(courtChargeId),
    ),
  ) {
    stubFor(
      post("/court-appearance/$courtAppearanceId/charge")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(CourtSentencingDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubRemoveCourtCharge(
    chargeId: String = UUID.randomUUID().toString(),
    courtAppearanceId: String = UUID.randomUUID().toString(),
  ) {
    stubFor(
      delete("/court-appearance/$courtAppearanceId/charge/$chargeId")
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubPutCourtChargeForAddExistingChargeToAppearance(
    courtChargeId: String = UUID.randomUUID().toString(),
    courtAppearanceId: String = UUID.randomUUID().toString(),
  ) {
    stubFor(
      put("/court-appearance/$courtAppearanceId/charge/$courtChargeId")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubPutChargeForUpdate(
    chargeId: UUID = UUID.randomUUID(),
    charge: CreateCharge = CreateCharge(
      offenceCode = "Code1",
      offenceStartDate = LocalDate.now(),
      outcome = "outcome",
      offenceEndDate = LocalDate.now().plusDays(1),
    ),
  ) {
    stubFor(
      put("/charge/$chargeId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(CourtSentencingDpsApiExtension.objectMapper.writeValueAsString(charge)),
        ),
    )
  }

  fun stubPostSentenceForCreate(
    sentenceId: String = UUID.randomUUID().toString(),
    response: CreateSentenceResponse = CreateSentenceResponse(
      sentenceUuid = sentenceId,
    ),
  ) {
    stubFor(
      post("/sentence")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(CourtSentencingDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubDeleteSentence(
    sentenceId: String = UUID.randomUUID().toString(),
  ) {
    stubFor(
      delete("/sentence/$sentenceId")
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubPutSentenceForUpdate(
    sentenceId: String = UUID.randomUUID().toString(),
    response: CreateSentenceResponse = CreateSentenceResponse(
      sentenceUuid = sentenceId,
    ),
  ) {
    stubFor(
      put("/sentence/$sentenceId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(CourtSentencingDpsApiExtension.objectMapper.writeValueAsString(response)),
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

  fun createCourtCaseMigrationCount() =
    findAll(WireMock.postRequestedFor(WireMock.urlMatching("/court-case"))).count()

  fun createCourtCaseForSynchronisationCount() =
    findAll(WireMock.postRequestedFor(WireMock.urlMatching("/court-case"))).count()
}
