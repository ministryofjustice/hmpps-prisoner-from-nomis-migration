package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus

class MappingApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val mappingApi = MappingApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    mappingApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    mappingApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    mappingApi.stop()
  }
}

class MappingApiMockServer : WireMockServer(WIREMOCK_PORT) {
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
      get(urlPathMatching("/mapping/visits/nomisId/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NOT_FOUND.value())
          .withBody("""{"message":"Not found"}""")
      )
    )
  }

  fun verifyGetVisitMappingByNomisId(times: Int = 1) {
    verify(
      times,
      getRequestedFor(
        urlPathMatching("/mapping/visits/nomisId/.*")
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
      get(urlPathMatching("/prison/$prisonId/room/nomis-room-id/.+?")).willReturn(
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
      get(urlPathMatching("/prison/$prisonId/room/nomis-room-id/.+?")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NOT_FOUND.value())
      )
    )
  }

  fun stubVisitMappingCreate() {
    stubFor(
      post(urlEqualTo("/mapping/visits")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
      )
    )
  }

  fun stubVisitMappingCreateFailureFollowedBySuccess() {
    stubFor(
      post(urlEqualTo("/mapping/visits"))
        .inScenario("Retry Visit Scenario")
        .whenScenarioStateIs(STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json")
        )
        .willSetStateTo("Cause Visit Success")
    )

    stubFor(
      post(urlEqualTo("/mapping/visits"))
        .inScenario("Retry Visit Scenario")
        .whenScenarioStateIs("Cause Visit Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
        )
    )
  }

  fun stubVisitMappingByMigrationId(whenCreated: String = "2020-01-01T11:10:00", count: Int = 278887) {
    stubFor(
      get(urlPathMatching("/mapping/visits/migration-id/.*")).willReturn(
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

  fun stubVisitMappingByNomisVisitId(
    whenCreated: String = "2020-01-01T11:10:00",
    nomisVisitId: Long = 191747,
    vsipId: String = "6c3ce237-f519-400d-85ca-9ba3e23323d8"
  ) {
    stubFor(
      get(urlPathMatching("/mapping/visits/nomisId/$nomisVisitId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
        {
            "nomisId": $nomisVisitId,
            "vsipId": "$vsipId",
            "label": "2022-02-14T09:58:45",
            "whenCreated": "$whenCreated",
            "mappingType": "MIGRATED"
        }          
            """.trimIndent()
          )
      )
    )
  }

  fun stubIncentiveMappingByMigrationId(whenCreated: String = "2020-01-01T11:10:00", count: Int = 278887) {
    stubFor(
      get(urlPathMatching("/mapping/incentives/migration-id/.*")).willReturn(
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
      get(urlEqualTo("/mapping/visits/migrated/latest")).willReturn(
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

  fun createVisitMappingCount() = findAll(postRequestedFor(urlEqualTo("/mapping/visits"))).count()

  fun verifyCreateMappingVisitIds(nomsVisitIds: Array<Long>, times: Int = 1) = nomsVisitIds.forEach {
    verify(
      times,
      postRequestedFor(urlEqualTo("/mapping/visits")).withRequestBody(
        matchingJsonPath(
          "nomisId",
          equalTo("$it")
        )
      )
    )
  }

  fun stubIncentiveMappingCreate() {
    stubFor(
      post(urlEqualTo("/mapping/incentives")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
      )
    )
  }

  fun stubIncentiveMappingByNomisIds(nomisBookingId: Long, nomisIncentiveSequence: Long, incentiveId: Long = 3) {
    stubFor(
      get(
        urlPathEqualTo("/mapping/incentives/nomis-booking-id/$nomisBookingId/nomis-incentive-sequence/$nomisIncentiveSequence")
      )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
        {
            "nomisBookingId": $nomisBookingId,
            "nomisIncentiveSequence": $nomisIncentiveSequence,
            "incentiveId": $incentiveId,
            "label": "2022-02-14T09:58:45",
            "whenCreated": "2022-02-16T16:21:15.589091",
            "mappingType": "MIGRATED"
        }         
              """.trimIndent()
            )
        )
    )
  }

  fun stubNomisIncentiveMappingNotFound(nomisBookingId: Long, nomisIncentiveSequence: Long) {
    stubFor(
      get(
        urlPathEqualTo("/mapping/incentives/nomis-booking-id/$nomisBookingId/nomis-incentive-sequence/$nomisIncentiveSequence")
      ).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NOT_FOUND.value())
          .withBody("""{"message":"Not found"}""")
      )
    )
  }

  fun stubAllNomisIncentiveMappingNotFound() {
    stubFor(
      get(
        urlPathMatching("/mapping/incentives/nomis-booking-id/\\d*/nomis-incentive-sequence/\\d*")
      ).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NOT_FOUND.value())
          .withBody("""{"message":"Not found"}""")
      )
    )
  }

  fun stubIncentiveMappingCreateFailureFollowedBySuccess() {
    stubFor(
      post(urlPathEqualTo("/mapping/incentives"))
        .inScenario("Retry Incentive Scenario")
        .whenScenarioStateIs(STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json")
        )
        .willSetStateTo("Cause Incentive Success")
    )

    stubFor(
      post(urlPathEqualTo("/mapping/incentives"))
        .inScenario("Retry Incentive Scenario")
        .whenScenarioStateIs("Cause Incentive Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
        )
        .willSetStateTo(STARTED)
    )
  }

  fun createIncentiveMappingCount() =
    findAll(postRequestedFor(urlPathEqualTo("/mapping/incentives"))).count()

  fun verifyCreateMappingIncentiveIds(nomsIncentiveIds: Array<Long>, times: Int = 1) = nomsIncentiveIds.forEach {
    verify(
      times,
      postRequestedFor(urlPathEqualTo("/mapping/incentives")).withRequestBody(
        matchingJsonPath(
          "incentiveId",
          equalTo("$it")
        )
      )
    )
  }

  fun verifyGetIncentiveMapping(nomisBookingId: Long, nomisIncentiveSequence: Long) {
    verify(
      getRequestedFor(
        urlPathEqualTo("/mapping/incentives/nomis-booking-id/$nomisBookingId/nomis-incentive-sequence/$nomisIncentiveSequence")
      )
    )
  }

  fun verifyCreateIncentiveMapping() {
    verify(
      postRequestedFor(
        urlPathEqualTo("/mapping/incentives")
      )
    )
  }
}
