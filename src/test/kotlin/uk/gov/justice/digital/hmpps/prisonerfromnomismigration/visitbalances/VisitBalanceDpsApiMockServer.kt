package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerMigrationDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerSyncDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody
import java.time.LocalDate

class VisitBalanceDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsVisitBalanceServer = VisitBalanceDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper

    @Suppress("unused")
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsVisitBalanceServer.getRequestBody(
      pattern,
      jsonMapper,
    )
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = dpsVisitBalanceServer.getRequestBodies(
      pattern,
      jsonMapper,
    )
  }
  override fun beforeAll(context: ExtensionContext) {
    dpsVisitBalanceServer.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    dpsVisitBalanceServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsVisitBalanceServer.stop()
  }
}

class VisitBalanceDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8101

    fun visitBalanceMigrationDto() = VisitAllocationPrisonerMigrationDto(
      prisonerId = "A1234BC",
      voBalance = 2,
      pvoBalance = 3,
      lastVoAllocationDate = LocalDate.of(2024, 3, 4),
    )
    fun visitBalanceAdjustmentSyncDto() = VisitAllocationPrisonerSyncDto(
      prisonerId = "A1234BC",
      oldVoBalance = 12,
      changeToVoBalance = 2,
      oldPvoBalance = 4,
      changeToPvoBalance = 1,
      createdDate = LocalDate.parse("2025-01-01"),
      adjustmentReasonCode = VisitAllocationPrisonerSyncDto.AdjustmentReasonCode.IEP,
      changeLogSource = VisitAllocationPrisonerSyncDto.ChangeLogSource.STAFF,
      comment = "Some comment",
    )
  }

  fun stubMigrateVisitBalance() {
    stubFor(
      post("/visits/allocation/prisoner/migrate")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun stubSyncVisitBalanceAdjustment() {
    stubFor(
      post("/visits/allocation/prisoner/sync")
        .willReturn(
          aResponse()
            .withStatus(200)
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
