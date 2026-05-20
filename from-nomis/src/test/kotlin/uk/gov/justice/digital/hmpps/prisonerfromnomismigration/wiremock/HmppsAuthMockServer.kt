package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class HmppsAuthApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val hmppsAuth = HmppsAuthMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    hmppsAuth.start()
    hmppsAuth.stubGrantToken()
  }

  override fun beforeEach(context: ExtensionContext) {
    hmppsAuth.resetAll()
    hmppsAuth.stubGrantToken()
  }

  override fun afterAll(context: ExtensionContext) {
    hmppsAuth.stop()
  }
}

class HmppsAuthMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8090
  }

  fun stubGrantToken() {
    stubFor(
      post(urlEqualTo("/auth/oauth/token"))
        .willReturn(
          aResponse()
            .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
            .withBody(
              """{
                    "token_type": "bearer",
                    "access_token": "ABCDE"
                }
              """.trimIndent(),
            ),
        ),
    )
  }

  @OptIn(ExperimentalEncodingApi::class)
  fun stubGrantToken(delayMs: Int, timeoutForAuthorizationHeader: String) {
    val encodedAuthHeader = Base64.encode(timeoutForAuthorizationHeader.toByteArray())
    stubFor(
      post(urlEqualTo("/auth/oauth/token"))
        .inScenario("TokenTimeoutScenario")
        .whenScenarioStateIs("Grant Timeout")
        .withHeader("Authorization", equalTo("Basic $encodedAuthHeader"))
        .willReturn(
          aResponse()
            .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
            .withBody(
              """{
                    "token_type": "bearer",
                    "access_token": "ABCDE"
                }
              """.trimIndent(),
            ).withFixedDelay(delayMs),
        )
        .willSetStateTo("Grant Success"),
    )

    stubFor(
      post(urlEqualTo("/auth/oauth/token"))
        .inScenario("TokenTimeoutScenario")
        .whenScenarioStateIs("Grant Success")
        .withHeader("Authorization", equalTo("Basic $encodedAuthHeader"))
        .willReturn(
          aResponse()
            .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
            .withBody(
              """{
                    "token_type": "bearer",
                    "access_token": "ABCDE"
                }
              """.trimIndent(),
            ),
        )
        .willSetStateTo(STARTED),
    )
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/auth/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }
}
