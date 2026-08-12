package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody

class AgencyRegistersDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    private var enableResetBeforeEach = true

    @JvmField
    val agencyRegistersApi = AgencyRegistersDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper

    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = agencyRegistersApi.getRequestBody(pattern, jsonMapper)

    fun resetAndDisableResetBeforeEach() {
      enableResetBeforeEach = false
      agencyRegistersApi.resetAll()
    }
  }

  override fun beforeAll(context: ExtensionContext) {
    agencyRegistersApi.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    if (enableResetBeforeEach) agencyRegistersApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    agencyRegistersApi.stop()
    enableResetBeforeEach = true
  }
}

class AgencyRegistersDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8109
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
