package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property.PropertyApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property.model.SyncPropertyContainerResponse
import java.util.UUID

class PropertyApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val propertiesDpsApi = PropertyDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    propertiesDpsApi.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    propertiesDpsApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    propertiesDpsApi.stop()
  }
}

class PropertyDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8107
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

  fun stubCreatePropertyForMigration(dpsId: String) {
    val response = samplePropertyInstance(UUID.fromString(dpsId))

    stubFor(
      post(urlMatching("/sync/property-containers/migrate"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubCreatePropertyForMigrationFailure(dpsId: String) {
    val response = samplePropertyInstance(UUID.fromString(dpsId))

    stubFor(
      post(urlMatching("/sync/property-containers/migrate"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value()),
        ),
    )
  }

  fun createPropertyCount() = findAll(postRequestedFor(urlMatching("/sync/property-containers/migrate"))).count()
}

fun samplePropertyInstance(dpsId: UUID) = SyncPropertyContainerResponse(
  dpsId = dpsId,
  nomisPropertyContainerId = 12345,
  mappingType = SyncPropertyContainerResponse.MappingType.CREATED,
)
