package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.prisonerlocation

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.stereotype.Component
import org.springframework.test.context.junit.jupiter.SpringExtension.getApplicationContext
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.prisonerlocation.PrisonerLocationDpsApiExtension.Companion.dpsPrisonerLocationServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.prisonerlocation.PrisonerLocationDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonerlocation.model.ResyncExternalMovementsRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonerlocation.model.ResyncResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.getRequestBody

class PrisonerLocationDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    private var enableResetBeforeEach = true

    @JvmField
    val dpsPrisonerLocationServer = PrisonerLocationDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper

    fun resetAndDisableResetBeforeEach() {
      enableResetBeforeEach = false
      dpsPrisonerLocationServer.resetAll()
    }
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsPrisonerLocationServer.start()
    jsonMapper = (getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    if (enableResetBeforeEach) dpsPrisonerLocationServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsPrisonerLocationServer.stop()
    enableResetBeforeEach = true
  }
}

@Component
class PrisonerLocationDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8111

    @Suppress("unused")
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsPrisonerLocationServer.getRequestBody(pattern, jsonMapper)
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = dpsPrisonerLocationServer.getRequestBodies(pattern, jsonMapper)

    fun resyncExternalMovementsRequest() = ResyncExternalMovementsRequest(
      custodialSeries = listOf(),
    )

    fun resyncResponse() = ResyncResponse(
      custodialSeries = listOf(),
    )
  }

  fun stubResyncPrisoner(personIdentifier: String = "A1234BC", response: ResyncResponse = resyncResponse()) {
    dpsPrisonerLocationServer.stubFor(
      put("/resync/external-movements/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubResyncPrisoner(
    personIdentifier: String = "A1234BC",
    status: Int,
    error: ErrorResponse = ErrorResponse(status = status),
  ) {
    dpsPrisonerLocationServer.stubFor(
      put("/resync/external-movements/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(error)),
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
