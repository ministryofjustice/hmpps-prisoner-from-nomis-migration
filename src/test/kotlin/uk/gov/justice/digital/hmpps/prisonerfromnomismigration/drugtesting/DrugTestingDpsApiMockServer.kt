package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting.model.MigrationRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting.model.Prisoner
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.LocalDate
import java.time.LocalDateTime

class DrugTestingDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    private var enableResetBeforeEach = true

    @JvmField
    val dpsDrugTestingServer = DrugTestingDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper

    @Suppress("unused")
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsDrugTestingServer.getRequestBody(pattern, jsonMapper)
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = dpsDrugTestingServer.getRequestBodies(pattern, jsonMapper)

    fun resetAndDisableResetBeforeEach() {
      enableResetBeforeEach = false
      dpsDrugTestingServer.resetAll()
    }
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsDrugTestingServer.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    if (enableResetBeforeEach) dpsDrugTestingServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsDrugTestingServer.stop()
    enableResetBeforeEach = true
  }
}

class DrugTestingDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8110

    fun migrationRequest() = MigrationRequest(
      dateGenerated = LocalDateTime.parse("2026-02-01T10:20:30"),
      createdBy = "billy",
      mainPercentage = 10,
      reservePercentage = 5,
      mainCount = 1,
      reserveCount = 2,
      eligibleCount = 20,
      prisoners = listOf(
        Prisoner(
          prisonerNumber = "A1234BC",
          listType = "M",
          listSelectionNumber = 1,
          testedStatus = true,
          reasonNotTested = null,
          notes = "Selected for testing",
        ),
      ),
    )
  }

  fun stubMigrate(prisonId: String = "MDI", rtpDate: LocalDate = LocalDate.parse("2025-07-01")) {
    stubFor(
      put("/resync/testing-lists/$prisonId/$rtpDate")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
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
