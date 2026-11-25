package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.status
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.model.LegacySentenceCalculationDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.model.LegacySentenceCalculationMigrationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates.model.LegacySentenceCalculationToCrdsMappingResult
import java.time.LocalDate
import java.time.LocalDateTime

class CalculateReleaseDatesDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val calculateReleaseDatesApi = CalculateReleaseDatesDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    calculateReleaseDatesApi.start()
    jsonMapper = SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper
  }

  override fun beforeEach(context: ExtensionContext) {
    calculateReleaseDatesApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    calculateReleaseDatesApi.stop()
  }
}

class CalculateReleaseDatesDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    const val WIREMOCK_PORT = 8113

    fun legacySentenceCalculation(
      nomisCalculationId: Long = 12345,
      prisonerId: String = "A1234KT",
      bookingId: Long = 54321,
    ) = LegacySentenceCalculationDto(
      nomisCalculationId = nomisCalculationId,
      prisonerId = prisonerId,
      bookingId = bookingId,
      calculationDate = LocalDateTime.parse("2024-01-02T03:04:05"),
      calculationReasonCode = "NEW_SENT",
      nomisCreatedAt = LocalDateTime.parse("2024-01-02T03:04:06"),
      nomisCreatedBy = "NOMIS.USER",
      commentText = "Calculation comment",
      hdcedCalculatedDate = LocalDate.parse("2025-01-02"),
      crdCalculatedDate = LocalDate.parse("2026-02-03"),
      sedCalculatedDate = LocalDate.parse("2027-03-04"),
    )

    fun mappingResult(nomisCalculationId: Long = 12345, crdsCalculationId: Long? = 6789) = LegacySentenceCalculationToCrdsMappingResult(nomisCalculationId = nomisCalculationId, crdsCalculationId = crdsCalculationId)

    fun migrationResponse(vararg results: LegacySentenceCalculationToCrdsMappingResult = arrayOf(mappingResult())) = LegacySentenceCalculationMigrationResponse(mappingResults = results.toList())
  }

  private val jsonMapper get() = CalculateReleaseDatesDpsApiExtension.jsonMapper

  fun stubMigratePrisoner(prisonerId: String = "A1234KT", response: LegacySentenceCalculationMigrationResponse = migrationResponse()) {
    stubFor(
      post("/legacy-sentence-calculation/migrate/$prisonerId")
        .willReturn(jsonOk(response)),
    )
  }

  fun stubSyncNew(response: LegacySentenceCalculationToCrdsMappingResult = mappingResult()) {
    stubFor(post("/legacy-sentence-calculation/sync").willReturn(jsonOk(response)))
  }

  fun stubSyncUpdated(response: LegacySentenceCalculationToCrdsMappingResult = mappingResult()) {
    stubFor(put("/legacy-sentence-calculation/sync").willReturn(jsonOk(response)))
  }

  fun stubSyncDeleted(nomisCalculationId: Long = 12345) {
    stubFor(delete("/legacy-sentence-calculation/sync/$nomisCalculationId").willReturn(status(HttpStatus.NO_CONTENT.value())))
  }

  fun stubGetAllForPrisoner(prisonerId: String = "A1234KT", response: List<LegacySentenceCalculationDto> = listOf(legacySentenceCalculation(prisonerId = prisonerId))) {
    stubFor(get("/legacy-sentence-calculation/$prisonerId").willReturn(jsonOk(response)))
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

  private fun jsonOk(body: Any) = aResponse()
    .withHeader("Content-Type", "application/json")
    .withBody(jsonMapper.writeValueAsString(body))
    .withStatus(HttpStatus.OK.value())
}
