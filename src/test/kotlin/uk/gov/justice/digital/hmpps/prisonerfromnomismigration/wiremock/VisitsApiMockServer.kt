package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus

class VisitsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val visitsApi = VisitsApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    visitsApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    visitsApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    visitsApi.stop()
  }
}

class VisitsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8082
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

  fun stubCreateVisit(httpResponse: HttpStatus = HttpStatus.CREATED) {
    stubFor(
      post(urlEqualTo("/migrate-visits")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(httpResponse.value())
          .withBody("654321"),
      ),
    )
  }

  fun stubCancelVisit(vsipReference: String) {
    stubFor(
      put(urlEqualTo("/migrate-visits/$vsipReference/cancel")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun verifyCancelVisit(times: Int) {
    verify(
      times,
      putRequestedFor(urlPathMatching("/migrate-visits/.+?/cancel")),
    )
  }

  fun createVisitCount() = findAll(postRequestedFor(urlEqualTo("/migrate-visits"))).count()
}
