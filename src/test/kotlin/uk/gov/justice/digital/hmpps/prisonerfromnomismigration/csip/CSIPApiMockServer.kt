package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.CREATED

class CSIPApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val csipApi = CSIPApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    csipApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    csipApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    csipApi.stop()
  }
}

class CSIPApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8088
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

  fun stubCSIPMigrate(dpsCSIPId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") {
    stubFor(
      post("/migrate/csip-report").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(
            CSIPMigrateResponse(
              dpsCSIPId = dpsCSIPId,
            ).toJson(),
          ),
      ),
    )
  }

  fun stubCSIPInsert(dpsCSIPId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") {
    stubFor(
      post("/csip").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(
            CSIPSyncResponse(
              dpsCSIPId = dpsCSIPId,
            ).toJson(),
          ),
      ),
    )
  }

  fun stubCSIPDelete(dpsCSIPId: String = "a1b2c3d4-e5f6-1234-5678-90a1b2c3d4e5") {
    stubFor(
      delete("/csip/$dpsCSIPId").willReturn(
        aResponse()
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCSIPDeleteNotFound(status: HttpStatus = HttpStatus.NOT_FOUND) {
    stubFor(
      delete(WireMock.urlPathMatching("/csip/.*"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value()),
        ),
    )
  }

  fun createCSIPMigrationCount() =
    findAll(postRequestedFor(urlMatching("/migrate/csip-report"))).count()

  fun createCSIPSyncCount() =
    findAll(postRequestedFor(urlMatching("/csip"))).count()
}

private fun Any.toJson(): String = ObjectMapper().writeValueAsString(this)