package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.havingExactly
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonDpsApiExtension.Companion.objectMapper

class PrisonPersonNomisSyncApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val nomisSyncApi = PrisonPersonNomisSyncApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    nomisSyncApi.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    nomisSyncApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    nomisSyncApi.stop()
  }
}

class PrisonPersonNomisSyncApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8098
  }

  fun stubSyncPhysicalAttributes(prisonerNumber: String = "A1234AA") {
    stubFor(
      put(urlPathMatching("/prisonperson/$prisonerNumber/physical-attributes"))
        .withQueryParam("fields", havingExactly("HEIGHT", "WEIGHT", "BUILD", "FACE", "FACIAL_HAIR", "HAIR", "LEFT_EYE_COLOUR", "RIGHT_EYE_COLOUR", "SHOE_SIZE"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200),
        ),
    )
  }

  fun stubSyncPhysicalAttributes(
    prisonerNumber: String = "A1234AA",
    status: HttpStatus,
  ) {
    stubFor(
      put(urlPathMatching("/prisonperson/$prisonerNumber/physical-attributes"))
        .withQueryParam("fields", havingExactly("HEIGHT", "WEIGHT", "BUILD", "FACE", "FACIAL_HAIR", "HAIR", "LEFT_EYE_COLOUR", "RIGHT_EYE_COLOUR", "SHOE_SIZE"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(ErrorResponse(status = status.value()))),
        ),
    )
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get(urlMatching("/health/ping"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(if (status == 200) "pong" else "some error")
            .withStatus(status),
        ),
    )
  }
}
