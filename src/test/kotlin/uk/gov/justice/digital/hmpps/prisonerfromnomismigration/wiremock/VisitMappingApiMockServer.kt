package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
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
      get(urlPathMatching("/mapping/nomisId/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NOT_FOUND.value())
          .withBody("""{"message":"Not found"}""")
      )
    )
  }

  fun stubRoomMapping() {
    stubFor(
      get(urlPathMatching("/prison/.+?/room/nomis-room-id/.+?")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            """
              {
                "vsipId": "1234",
                "isOpen": true
              }
            """.trimIndent()
          )
      )
    )
  }

  fun stubRoomMapping(prisonId: String) {
    stubFor(
      get(urlPathMatching("/prison/${prisonId}/room/nomis-room-id/.+?")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            """
              {
                "vsipId": "1234",
                "isOpen": true
              }
            """.trimIndent()
          )
      )
    )
  }

  fun stubMissingRoomMapping(prisonId: String) {
    stubFor(
      get(urlPathMatching("/prison/${prisonId}/room/nomis-room-id/.+?")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NOT_FOUND.value())
      )
    )
  }

  fun stubVisitMappingCreate() {
    stubFor(
      post(urlEqualTo("/mapping")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
      )
    )
  }

  fun stubVisitMappingCreateFailureFollowedBySuccess() {
    stubFor(
      post(urlEqualTo("/mapping"))
        .inScenario("Retry Scenario")
        .whenScenarioStateIs(STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json")
        )
        .willSetStateTo("Cause Success")
    )

    stubFor(
      post(urlEqualTo("/mapping"))
        .inScenario("Retry Scenario")
        .whenScenarioStateIs("Cause Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
        )
    )
  }

  fun stubVisitMappingByMigrationId(whenCreated: String = "2020-01-01T11:10:00", count: Int = 278887) {
    stubFor(
      get(urlPathMatching("/mapping/migration-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
{
    "content": [
        {
            "nomisId": 191747,
            "vsipId": "6c3ce237-f519-400d-85ca-9ba3e23323d8",
            "label": "2022-02-14T09:58:45",
            "whenCreated": "$whenCreated",
            "mappingType": "MIGRATED"
        }
    ],
    "pageable": {
        "sort": {
            "empty": true,
            "sorted": false,
            "unsorted": true
        },
        "offset": 0,
        "pageSize": 1,
        "pageNumber": 0,
        "paged": true,
        "unpaged": false
    },
    "last": false,
    "totalPages": 278887,
    "totalElements": $count,
    "size": 1,
    "number": 0,
    "sort": {
        "empty": true,
        "sorted": false,
        "unsorted": true
    },
    "first": true,
    "numberOfElements": 1,
    "empty": false
}            
            """.trimIndent()
          )
      )
    )
  }

  fun stubLatestMigration(migrationId: String) {
    stubFor(
      get(urlEqualTo("/mapping/migrated/latest")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
{
    "nomisId": 14478380,
    "vsipId": "7fd62066-9aff-4f77-bdee-9d92aafc5555",
    "label": "$migrationId",
    "mappingType": "MIGRATED",
    "whenCreated": "2022-02-16T16:21:15.589091"
}              
              """
          )
      )
    )
  }

  fun createVisitMappingCount() = findAll(postRequestedFor(urlEqualTo("/mapping"))).count()

  fun verifyCreateMappingVisitIds(nomsVisitIds: Array<Long>, times: Int = 1) = nomsVisitIds.forEach {
    verify(
      times,
      postRequestedFor(urlEqualTo("/mapping")).withRequestBody(
        WireMock.matchingJsonPath(
          "nomisId",
          WireMock.equalTo("$it")
        )
      )
    )
  }
}
