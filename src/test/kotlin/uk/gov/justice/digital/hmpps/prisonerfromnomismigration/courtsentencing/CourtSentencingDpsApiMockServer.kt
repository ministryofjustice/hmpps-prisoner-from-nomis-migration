package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.CreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyChargeCreatedResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCourtAppearanceCreatedResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacyCourtCaseCreatedResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.LegacySentenceCreatedResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.model.MigrationCreateCourtCasesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.util.UUID

class CourtSentencingDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsCourtSentencingServer = CourtSentencingDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsCourtSentencingServer.getRequestBody(
      pattern,
      objectMapper,
    )
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

private const val NOMIS_CASE_ID = 12345L

class CourtSentencingDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8094
  }

  fun stubPostCourtCaseForCreate(
    courtCaseId: String = UUID.randomUUID().toString(),
    response: LegacyCourtCaseCreatedResponse = LegacyCourtCaseCreatedResponse(
      courtCaseUuid = courtCaseId,
    ),
  ) {
    stubFor(
      post("/legacy/court-case")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(CourtSentencingDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubPostCourtCasesForCreateMigration(
    courtCaseId: String = UUID.randomUUID().toString(),
    response: MigrationCreateCourtCasesResponse = MigrationCreateCourtCasesResponse(
      courtCases = listOf(
        MigrationCreateCourtCaseResponse(
          courtCaseUuid = courtCaseId,
          caseId = NOMIS_CASE_ID,
        ),
      ),
      charges = emptyList(),
      appearances = emptyList(),
      sentences = emptyList(),
      sentenceTerms = emptyList(),
    ),
  ) {
    stubFor(
      post("/legacy/court-case/migration")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(CourtSentencingDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubUpdateCourtCasePostMerge(
    courtCasesCreated: MigrationCreateCourtCasesResponse = MigrationCreateCourtCasesResponse(
      courtCases = emptyList(),
      appearances = emptyList(),
      charges = emptyList(),
      sentences = emptyList(),
      sentenceTerms = emptyList(),
    ),
    courtCasesDeactivatedIds: List<String> = emptyList(),
    sentencesDeactivatedIds: List<String> = emptyList(),
  ) {
    // TODO - switch to real merge endpoint when available
    courtCasesDeactivatedIds.forEach { courtCaseId ->
      stubPutCourtCaseForUpdate(courtCaseId = courtCaseId)
    }
    sentencesDeactivatedIds.forEach { sentenceId ->
      stubPutSentenceForUpdate(sentenceId = sentenceId)
    }
    stubPostCourtCasesForCreateMigration(response = courtCasesCreated)
  }

  fun stubPutCourtCaseForUpdate(
    courtCaseId: String = UUID.randomUUID().toString(),
    response: CreateCourtCaseResponse = CreateCourtCaseResponse(
      courtCaseUuid = courtCaseId,
      appearances = emptyList(),
      charges = emptyList(),
    ),
  ) {
    stubFor(
      put("/legacy/court-case/$courtCaseId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(CourtSentencingDpsApiExtension.objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubPutCaseIdentifierRefresh(
    courtCaseId: String = UUID.randomUUID().toString(),
  ) {
    stubFor(
      put("/court-case/$courtCaseId/case-references/refresh")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubDeleteCourtCase(
    courtCaseId: String = UUID.randomUUID().toString(),
  ) {
    stubFor(
      delete("/legacy/court-case/$courtCaseId")
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubPostCourtAppearanceForCreate(
    courtAppearanceId: UUID = UUID.randomUUID(),
    courtCaseId: String = "12345",
    response: LegacyCourtAppearanceCreatedResponse = LegacyCourtAppearanceCreatedResponse(
      lifetimeUuid = courtAppearanceId,
      courtCaseUuid = courtCaseId,
      prisonerId = "A1234AA",
    ),
  ) {
    stubFor(
      post("/legacy/court-appearance")
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
      put("/legacy/court-appearance/$courtAppearanceId")
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
      delete("/legacy/court-appearance/$courtAppearanceId")
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubPostCourtChargeForCreate(
    courtChargeId: String = UUID.randomUUID().toString(),
    courtCaseId: String = UUID.randomUUID().toString(),
    offenderNo: String,
    response: LegacyChargeCreatedResponse = LegacyChargeCreatedResponse(
      lifetimeUuid = UUID.fromString(courtChargeId),
      courtCaseUuid = courtCaseId,
      prisonerId = offenderNo,
    ),
  ) {
    stubFor(
      post("/legacy/charge")
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
      delete("/legacy/court-appearance/$courtAppearanceId/charge/$chargeId")
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
      put("/legacy/court-appearance/$courtAppearanceId/charge/$courtChargeId")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubPutChargeForUpdate(
    chargeId: UUID = UUID.randomUUID(),
  ) {
    stubFor(
      put("/legacy/charge/$chargeId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubPutAppearanceChargeForUpdate(
    chargeId: String = UUID.randomUUID().toString(),
    appearanceId: String = UUID.randomUUID().toString(),
  ) {
    stubFor(
      put("/legacy/charge/$chargeId/appearance/$appearanceId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubPostSentenceForCreate(
    sentenceId: String = UUID.randomUUID().toString(),
    response: LegacySentenceCreatedResponse = LegacySentenceCreatedResponse(
      lifetimeUuid = UUID.fromString(sentenceId),
      courtCaseId = UUID.randomUUID().toString(),
      chargeLifetimeUuid = UUID.randomUUID(),
      appearanceUuid = UUID.randomUUID(),
      prisonerId = "A1234AA",
      createdPeriodLengths = emptyList(),
    ),
  ) {
    stubFor(
      post("/legacy/sentence")
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
      delete("/legacy/sentence/$sentenceId")
        .willReturn(
          aResponse()
            .withStatus(204)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubPutSentenceForUpdate(
    sentenceId: String = UUID.randomUUID().toString(),
  ) {
    stubFor(
      put("/legacy/sentence/$sentenceId")
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

  fun createCourtCaseByOffenderMigrationCount() = findAll(WireMock.postRequestedFor(WireMock.urlMatching("/legacy/court-case/migration"))).count()
}
