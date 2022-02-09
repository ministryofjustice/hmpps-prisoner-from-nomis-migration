package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus

class VisitMappingApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val visitMappingApi = VisitMappingApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    visitMappingApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    visitMappingApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    visitMappingApi.stop()
  }
}

class VisitMappingApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8083
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )
  }

  fun stubNomisVisitNotFound() {
    stubFor(
      get(WireMock.urlPathMatching("/mapping/nomisId/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NOT_FOUND.value())
          .withBody("""{"message":"Not found"}""")
      )
    )
  }

  fun stubRoomMapping() {
    stubFor(
      get(WireMock.urlPathMatching("/prison/.+?/room/nomis-room-id/.+?")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            """
              {
                "vsipRoomId": "1234",
                "isOpen": true
              }
            """.trimIndent()
          )
      )
    )
  }
}
