package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitorders

import com.fasterxml.jackson.databind.ObjectMapper
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visit.balance.model.VisitAllocationPrisonerMigrationDto
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
    lateinit var objectMapper: ObjectMapper

    @Suppress("unused")
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsVisitBalanceServer.getRequestBody(
      pattern,
      objectMapper,
    )
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = dpsVisitBalanceServer.getRequestBodies(
      pattern,
      objectMapper,
    )
  }
  override fun beforeAll(context: ExtensionContext) {
    dpsVisitBalanceServer.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
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
