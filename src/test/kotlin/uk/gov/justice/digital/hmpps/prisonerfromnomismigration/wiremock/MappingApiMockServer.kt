package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.created
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
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
    const val VISITS_CREATE_MAPPING_URL = "/mapping/visits"
    const val VISITS_GET_MAPPING_URL = "/mapping/visits/nomisId"
    const val ADJUDICATIONS_GET_MAPPING_URL = "/mapping/adjudications/adjudication-number"
    const val APPOINTMENTS_CREATE_MAPPING_URL = "/mapping/appointments"
    const val APPOINTMENTS_GET_MAPPING_URL = "/mapping/appointments/nomis-event-id"
    const val SENTENCE_ADJUSTMENTS_GET_MAPPING_URL =
      "/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id"
    const val KEYDATE_ADJUSTMENTS_GET_MAPPING_URL =
      "/mapping/sentencing/adjustments/nomis-adjustment-category/KEY-DATE/nomis-adjustment-id"
    const val ADJUSTMENTS_CREATE_MAPPING_URL = "/mapping/sentencing/adjustments"
  }

  override fun beforeAll(context: ExtensionContext) {
    mappingApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    mappingApi.resetAll()
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
          .withStatus(status),
      ),
    )
  }

  fun verifyGetVisitMappingByNomisId(times: Int = 1) {
    verify(
      times,
      getRequestedFor(
        urlPathMatching("/mapping/visits/nomisId/.*"),
      ),
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
            """.trimIndent(),
          ),
      ),
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
            """.trimIndent(),
          ),
      ),
    )
  }

  fun stubMissingRoomMapping(prisonId: String) {
    stubFor(
      get(urlPathMatching("/prison/$prisonId/room/nomis-room-id/.+?")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NOT_FOUND.value()),
      ),
    )
  }

  fun stubVisitMappingByMigrationId(whenCreated: String = "2020-01-01T11:10:00", count: Int = 278887) {
    val content = """{
      "nomisId": 191747,
      "vsipId": "6c3ce237-f519-400d-85ca-9ba3e23323d8",
      "label": "2022-02-14T09:58:45",
      "whenCreated": "$whenCreated",
      "mappingType": "MIGRATED"
    }"""
    stubFor(
      get(urlPathMatching("/mapping/visits/migration-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            pageContent(content, count),
          ),
      ),
    )
  }

  fun stubVisitMappingByNomisVisitId(
    whenCreated: String = "2020-01-01T11:10:00",
    nomisVisitId: Long = 191747,
    vsipId: String = "6c3ce237-f519-400d-85ca-9ba3e23323d8",
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
            """.trimIndent(),
          ),
      ),
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
              """,
          ),
      ),
    )
  }

  fun verifyCreateMappingVisitIds(nomsVisitIds: Array<Long>, times: Int = 1) = nomsVisitIds.forEach {
    verify(
      times,
      postRequestedFor(urlEqualTo("/mapping/visits")).withRequestBody(
        matchingJsonPath(
          "nomisId",
          equalTo("$it"),
        ),
      ),
    )
  }

  fun stubVisitsCreateConflict(
    existingVsipId: Long = 10,
    duplicateVsipId: Long = 11,
    nomisVisitId: Long = 123,
  ) {
    stubFor(
      post(urlPathEqualTo("/mapping/visits"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "vsipId": $existingVsipId,
                  "nomisId": $nomisVisitId,
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                 },
                 "duplicate" : {
                  "vsipId": $duplicateVsipId,
                  "nomisId": $nomisVisitId,
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                  }
              }
              }""",
            ),
        ),
    )
  }

  fun stubGetNomisSentencingAdjustment(
    adjustmentCategory: String = "SENTENCE",
    nomisAdjustmentId: Long = 987L,
    adjustmentId: String = "567S",
  ) {
    stubFor(
      get(
        urlPathMatching("/mapping/sentencing/adjustments/nomis-adjustment-category/$adjustmentCategory/nomis-adjustment-id/$nomisAdjustmentId"),
      ).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            """
            {
            "adjustmentId": "$adjustmentId",
            "nomisAdjustmentId": $nomisAdjustmentId,
            "nomisAdjustmentCategory": "$adjustmentCategory",
            "label": "2022-02-14T09:58:45",
            "whenCreated": "2022-02-14T09:58:45",
            "mappingType": "MIGRATED"
            }
            """.trimMargin(),
          ),
      ),
    )
  }

  fun stubSentenceAdjustmentMappingDelete(adjustmentId: String = "567S") {
    stubFor(
      delete(urlEqualTo("/mapping/sentencing/adjustments/adjustment-id/$adjustmentId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubSentenceAdjustmentMappingByMigrationId(whenCreated: String = "2020-01-01T11:10:00", count: Int = 278887) {
    val content = """{
      "adjustmentId": 191747,
      "nomisAdjustmentId": 123,
      "nomisAdjustmentCategory": "SENTENCE",
      "label": "2022-02-14T09:58:45",
      "whenCreated": "$whenCreated",
      "mappingType": "MIGRATED"
    }"""
    stubFor(
      get(urlPathMatching("/mapping/sentencing/adjustments/migration-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(pageContent(content, count)),
      ),
    )
  }

  fun stubSentenceAdjustmentMappingCreateConflict(
    existingAdjustmentId: String = "10",
    duplicateAdjustmentId: String = "11",
    nomisAdjustmentId: Long = 123,
  ) {
    stubFor(
      post(urlPathEqualTo("/mapping/sentencing/adjustments"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "adjustmentId": "$existingAdjustmentId",
                  "nomisAdjustmentId": $nomisAdjustmentId,
                  "nomisAdjustmentCategory": "SENTENCE",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                 },
                 "duplicate" : {
                  "adjustmentId": "$duplicateAdjustmentId",
                  "nomisAdjustmentId": $nomisAdjustmentId,
                  "nomisAdjustmentCategory": "SENTENCE",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                  }
              }
              }""",
            ),
        ),
    )
  }

  fun stubSentenceAdjustmentMappingCreateFailure() {
    stubFor(
      post(urlPathEqualTo("/mapping/sentencing/adjustments"))
        .willReturn(
          aResponse()
            .withStatus(500)
            .withHeader("Content-Type", "application/json"),
        ),
    )
  }

  fun verifyCreateMappingSentenceAdjustmentIds(nomsSentenceAdjustmentIds: Array<String>, times: Int = 1) =
    nomsSentenceAdjustmentIds.forEach {
      verify(
        times,
        postRequestedFor(urlPathEqualTo("/mapping/sentencing/adjustments")).withRequestBody(
          matchingJsonPath(
            "adjustmentId",
            equalTo(it),
          ),
        ),
      )
    }

  fun stubNomisAppointmentsMappingFound(id: Long) {
    val content = """{
      "appointmentInstanceId": 191747,
      "nomisEventId": $id,
      "label": "2022-02-14T09:58:45",
      "whenCreated": "2022-10-01T11:10:00",
      "mappingType": "MIGRATED"
    }"""
    stubFor(
      get(urlPathEqualTo("/mapping/appointments/nomis-event-id/$id"))
        .atPriority(1)
        .willReturn(okJson(content)),
    )
  }

  fun stubAppointmentMappingByMigrationId(whenCreated: String = "2020-01-01T11:10:00", count: Int = 278887) {
    val content = """{
      "appointmentInstanceId": 191747,
      "nomisEventId": 123,
      "label": "2022-02-14T09:58:45",
      "whenCreated": "$whenCreated",
      "mappingType": "MIGRATED"
    }"""
    stubFor(
      get(urlPathMatching("/mapping/appointments/migration-id/.*")).willReturn(
        okJson(pageContent(content, count)),
      ),
    )
  }

  fun stubAppointmentMappingCreateConflict(
    existingAppointmentInstanceId: Long = 10,
    duplicateAppointmentInstanceId: Long = 11,
    nomisEventId: Long = 123,
  ) {
    stubFor(
      post(urlPathEqualTo("/mapping/appointments"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "appointmentInstanceId": $existingAppointmentInstanceId,
                  "nomisEventId": $nomisEventId,
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                 },
                 "duplicate" : {
                  "appointmentInstanceId": $duplicateAppointmentInstanceId,
                  "nomisEventId": $nomisEventId,
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                  }
              }
              }""",
            ),
        ),
    )
  }

  fun verifyCreateMappingAppointmentIds(nomisAppointmentIds: Array<String>, times: Int = 1) =
    nomisAppointmentIds.forEach {
      verify(
        times,
        postRequestedFor(urlPathEqualTo("/mapping/appointments")).withRequestBody(
          matchingJsonPath(
            "appointmentInstanceId",
            equalTo(it),
          ),
        ),
      )
    }

  fun stubAdjudicationMappingByMigrationId(whenCreated: String = "2020-01-01T11:10:00", count: Int = 278887) {
    val content = """{
      "adjudicationNumber": 191747,
      "chargeSequence": 1,
      "chargeNumber": "191747/1",
      "label": "2022-02-14T09:58:45",
      "whenCreated": "$whenCreated",
      "mappingType": "MIGRATED"
    }"""
    stubFor(
      get(urlPathMatching("/mapping/adjudications/migration-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(pageContent(content, count)),
      ),
    )
  }

  fun verifyCreateMappingAdjudication(
    adjudicationNumber: Long,
    chargeSequence: Int,
    chargeNumber: String,
    times: Int = 1,
  ) {
    verify(
      times,
      postRequestedFor(urlPathEqualTo("/mapping/adjudications"))
        .withRequestBody(matchingJsonPath("adjudicationNumber", equalTo(adjudicationNumber.toString())))
        .withRequestBody(matchingJsonPath("chargeSequence", equalTo(chargeSequence.toString())))
        .withRequestBody(matchingJsonPath("chargeNumber", equalTo(chargeNumber))),
    )
  }

  fun verifyCreateMappingAdjudication(builder: RequestPatternBuilder.() -> RequestPatternBuilder = { this }) =
    verify(
      postRequestedFor(urlEqualTo("/mapping/adjudications")).builder(),
    )

  private fun pageContent(content: String, count: Int) = """
  {
      "content": [
        $content
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

  fun stubAllMappingsNotFound(url: String) {
    stubFor(
      get(
        urlPathMatching("$url/\\d*"),
      ).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NOT_FOUND.value())
          .withBody("""{"message":"Not found"}"""),
      ),
    )
  }

  fun stubMappingCreate(url: String) {
    stubFor(
      post(urlEqualTo(url)).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value()),
      ),
    )
  }

  fun stubMappingCreateFailureFollowedBySuccess(url: String) {
    stubFor(
      post(urlPathEqualTo(url))
        .inScenario("Retry create Scenario")
        .whenScenarioStateIs(STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause create Success"),
    )

    stubFor(
      post(urlPathEqualTo(url))
        .inScenario("Retry create Scenario")
        .whenScenarioStateIs("Cause create Success")
        .willReturn(created())
        .willSetStateTo(STARTED),
    )
  }

  fun createMappingCount(url: String) =
    findAll(postRequestedFor(urlPathEqualTo(url))).count()
}
