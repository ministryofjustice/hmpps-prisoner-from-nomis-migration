package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class SentencingApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val sentencingApi = SentencingApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    sentencingApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    sentencingApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    sentencingApi.stop()
  }
}

class SentencingApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8085
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
}
