package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
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
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.HttpStatus.OK
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.LegacyLocation
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.LocationsApiExtension.Companion.jsonMapper
import java.time.LocalDateTime
import java.util.UUID

class LocationsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val locationsApi = LocationsApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    locationsApi.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    locationsApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    locationsApi.stop()
  }
}

class LocationsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8093
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

  fun stubUpsertLocationForSynchronisation(locationId: String = "f1c1e3e3-3e3e-3e3e-3e3e-3e3e3e3e3e3e") {
    stubFor(
      post("/sync/upsert").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(OK.value())
          .withBody(aLocation(locationId)),
      ),
    )
  }

  fun stubUpsertLocationForSynchronisationWithError(error: ErrorResponse) {
    stubFor(
      post("/sync/upsert").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(error.status)
          .withBody(error),
      ),
    )
  }

  fun stubDeleteLocation(locationId: String) {
    stubFor(
      delete("/sync/delete/$locationId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(NO_CONTENT.value()),
      ),
    )
  }

  fun stubDeleteLocationWithError(locationId: String, status: Int = 500) {
    stubFor(
      delete("/sync/delete/$locationId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status),
      ),
    )
  }

  fun createLocationSynchronisationCount() = findAll(postRequestedFor(urlMatching("/sync/upsert"))).count()

  private fun aLocation(locationId: String) = LegacyLocation(
    id = UUID.fromString(locationId),
    locationType = LegacyLocation.LocationType.WING,
    code = "C",
    localName = "Wing C",
    prisonId = "MDI",
    comments = "Test comment",
    active = true,
    key = "key",
    pathHierarchy = "MDI-C",
    lastModifiedBy = "me",
    lastModifiedDate = LocalDateTime.parse("2024-05-25T12:40"),
  )
  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder = this.withBody(jsonMapper.writeValueAsString(body))
}
