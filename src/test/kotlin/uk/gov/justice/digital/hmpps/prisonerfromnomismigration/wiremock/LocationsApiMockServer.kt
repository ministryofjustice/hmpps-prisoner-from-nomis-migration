package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
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
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.HttpStatus.OK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.model.Location
import java.util.UUID

class LocationsApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val locationsApi = LocationsApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    locationsApi.start()
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

  fun stubUpsertLocationForMigration(locationId: String = "f1c1e3e3-3e3e-3e3e-3e3e-3e3e3e3e3e3e") {
    stubFor(
      post("/migrate/location").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(CREATED.value())
          .withBody(aLocation(locationId)),
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

  fun createLocationMigrationCount() =
    findAll(postRequestedFor(urlMatching("/migrate/location"))).count()

  fun createLocationSynchronisationCount() =
    findAll(postRequestedFor(urlMatching("/sync/upsert"))).count()

  private fun aLocation(locationId: String) = Location(
    id = UUID.fromString(locationId),
    locationType = Location.LocationType.WING,
    code = "C",
    localName = "Wing C",
    prisonId = "MDI",
    comments = "Test comment",
    active = true,
    isResidential = true,
    key = "key",
    topLevelId = UUID.fromString("f1c1e3e3-3e3e-3e3e-3e3e-3e3e3e3e3e3e"),
    pathHierarchy = "MDI-C",
    deactivatedByParent = false,
    permanentlyInactive = false,
  ).toJson()
}

private fun Any.toJson(): String = ObjectMapper().writeValueAsString(this)
