package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.CalculateReleaseDatesApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.model.LegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.model.LegacySentenceCalculationMigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.model.LegacySentenceCalculationMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.model.LegacySentenceCalculationToCrdsMappingResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.LocalDate
import java.time.LocalDateTime

class CalculateReleaseDatesApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val calculateReleaseDatesApi = CalculateReleaseDatesApiMockServer()
    lateinit var jsonMapper: JsonMapper

    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = calculateReleaseDatesApi.getRequestBody(pattern, jsonMapper)

    fun legacySentenceCalculationDto(nomisCalculationId: Long = 12345, prisonerId: String = "A1234BC") = LegacySentenceCalculationDto(
      nomisCalculationId = nomisCalculationId,
      prisonerId = prisonerId,
      bookingId = 54321,
      calculationDate = LocalDateTime.parse("2024-01-02T10:00:00"),
      calculationReasonCode = "NEW",
      nomisCreatedAt = LocalDateTime.parse("2024-01-02T10:00:00"),
      nomisCreatedBy = "TEST_USER",
      sedCalculatedDate = LocalDate.parse("2030-01-01"),
    )

    fun legacySentenceCalculationToCrdsMappingResult(nomisCalculationId: Long = 12345, crdsCalculationId: Long? = null) = LegacySentenceCalculationToCrdsMappingResult(nomisCalculationId = nomisCalculationId, crdsCalculationId = crdsCalculationId)

    fun legacySentenceCalculationMigrationRequest() = LegacySentenceCalculationMigrationRequest(sentenceCalculations = listOf(legacySentenceCalculationDto()))

    fun legacySentenceCalculationMigrationResponse() = LegacySentenceCalculationMigrationResponse(mappingResults = listOf(legacySentenceCalculationToCrdsMappingResult()))
  }

  override fun beforeAll(context: ExtensionContext) {
    jsonMapper = SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper
    calculateReleaseDatesApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    calculateReleaseDatesApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    calculateReleaseDatesApi.stop()
  }
}

class CalculateReleaseDatesApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8113
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

  fun stubSyncNew(response: LegacySentenceCalculationToCrdsMappingResult = CalculateReleaseDatesApiExtension.legacySentenceCalculationToCrdsMappingResult()) {
    stubFor(
      post(urlPathEqualTo("/legacy-sentence-calculation/sync")).willReturn(jsonResponse(jsonMapper.writeValueAsString(response))),
    )
  }

  fun stubSyncUpdated(response: LegacySentenceCalculationToCrdsMappingResult = CalculateReleaseDatesApiExtension.legacySentenceCalculationToCrdsMappingResult()) {
    stubFor(
      put(urlPathEqualTo("/legacy-sentence-calculation/sync")).willReturn(jsonResponse(jsonMapper.writeValueAsString(response))),
    )
  }

  fun stubSyncDeleted(nomisCalculationId: Long = 12345) {
    stubFor(
      delete(urlPathEqualTo("/legacy-sentence-calculation/sync/$nomisCalculationId")).willReturn(aResponse().withStatus(204)),
    )
  }

  fun stubMigrate(prisonerId: String = "A1234BC", response: LegacySentenceCalculationMigrationResponse = CalculateReleaseDatesApiExtension.legacySentenceCalculationMigrationResponse()) {
    stubFor(
      post(urlPathEqualTo("/legacy-sentence-calculation/migrate/$prisonerId")).willReturn(jsonResponse(jsonMapper.writeValueAsString(response))),
    )
  }

  fun stubGetAllForPrisoner(prisonerId: String = "A1234BC", response: List<LegacySentenceCalculationDto> = listOf(CalculateReleaseDatesApiExtension.legacySentenceCalculationDto())) {
    stubFor(
      get(urlPathEqualTo("/legacy-sentence-calculation/$prisonerId")).willReturn(jsonResponse(jsonMapper.writeValueAsString(response))),
    )
  }

  private fun jsonResponse(body: String) = aResponse()
    .withHeader("Content-Type", "application/json")
    .withStatus(200)
    .withBody(body)
}
