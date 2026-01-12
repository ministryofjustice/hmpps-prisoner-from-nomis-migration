package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
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
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.matching.UrlPattern
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.net.URLEncoder

class MappingApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {

  companion object {
    @JvmField
    val mappingApi = MappingApiMockServer()
    const val VISITS_CREATE_MAPPING_URL = "/mapping/visits"
    const val VISITS_GET_MAPPING_URL = "/mapping/visits/nomisId"
    const val ACTIVITIES_CREATE_MAPPING_URL = "/mapping/activities/migration"
    const val ACTIVITIES_GET_MAPPING_URL = "/mapping/activities/migration/nomis-course-activity-id"
    const val ALLOCATIONS_CREATE_MAPPING_URL = "/mapping/allocations/migration"
    const val ALLOCATIONS_GET_MAPPING_URL = "/mapping/allocations/migration/nomis-allocation-id"
    const val APPOINTMENTS_CREATE_MAPPING_URL = "/mapping/appointments"
    const val APPOINTMENTS_GET_MAPPING_URL = "/mapping/appointments/nomis-event-id"
    const val SENTENCE_ADJUSTMENTS_GET_MAPPING_URL =
      "/mapping/sentencing/adjustments/nomis-adjustment-category/SENTENCE/nomis-adjustment-id"
    const val KEYDATE_ADJUSTMENTS_GET_MAPPING_URL =
      "/mapping/sentencing/adjustments/nomis-adjustment-category/KEY_DATE/nomis-adjustment-id"
    const val ADJUSTMENTS_CREATE_MAPPING_URL = "/mapping/sentencing/adjustments"
    const val LOCATIONS_CREATE_MAPPING_URL = "/mapping/locations"
    const val LOCATIONS_GET_MAPPING_URL = "/mapping/locations/nomis"
    lateinit var objectMapper: ObjectMapper
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = mappingApi.getRequestBodies(
      pattern,
      objectMapper,
    )
  }

  override fun beforeAll(context: ExtensionContext) {
    mappingApi.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jackson2ObjectMapper") as ObjectMapper)
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

  fun stubGetNomisSentencingAdjustment(
    adjustmentCategory: String = "SENTENCE",
    nomisAdjustmentId: Long = 987L,
    status: HttpStatus,
  ) {
    stubFor(
      get(
        urlPathMatching("/mapping/sentencing/adjustments/nomis-adjustment-category/$adjustmentCategory/nomis-adjustment-id/$nomisAdjustmentId"),
      ).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody("""{"message":"Not found"}"""),
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

  fun stubSentenceAdjustmentMappingCreateConflict(
    existingAdjustmentId: String = "10",
    duplicateAdjustmentId: String = "11",
    nomisAdjustmentId: Long = 123,
    nomisAdjustmentCategory: String = "SENTENCE",
  ) {
    stubFor(
      post(urlPathEqualTo("/mapping/sentencing/adjustments"))
        .willReturn(
          conflict(
            existingAdjustmentId = existingAdjustmentId,
            duplicateAdjustmentId = duplicateAdjustmentId,
            nomisAdjustmentId = nomisAdjustmentId,
            nomisAdjustmentCategory = nomisAdjustmentCategory,
          ),
        ),
    )
  }

  fun stubSentenceAdjustmentMappingCreateConflictAfter500Error(
    existingAdjustmentId: String = "10",
    duplicateAdjustmentId: String = "11",
    nomisAdjustmentId: Long = 123,
    nomisAdjustmentCategory: String = "SENTENCE",
  ) {
    val url = urlPathEqualTo("/mapping/sentencing/adjustments")
    stubFor(
      post(url)
        .inScenario("Retry create conflict scenario")
        .whenScenarioStateIs(STARTED)
        .willReturn(
          aResponse()
            .withStatus(500)
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause create conflict"),
    )

    stubFor(
      post(url)
        .inScenario("Retry create conflict scenario")
        .whenScenarioStateIs("Cause create conflict")
        .willReturn(
          conflict(
            existingAdjustmentId = existingAdjustmentId,
            duplicateAdjustmentId = duplicateAdjustmentId,
            nomisAdjustmentId = nomisAdjustmentId,
            nomisAdjustmentCategory = nomisAdjustmentCategory,
          ),
        )
        .willSetStateTo(STARTED),
    )
  }

  private fun conflict(
    existingAdjustmentId: String = "10",
    duplicateAdjustmentId: String = "11",
    nomisAdjustmentId: Long = 123,
    nomisAdjustmentCategory: String = "SENTENCE",
  ): ResponseDefinitionBuilder = aResponse()
    .withStatus(409)
    .withHeader("Content-Type", "application/json")
    .withBody(
      """{
              "moreInfo": 
              {
                "existing" :  {
                  "adjustmentId": "$existingAdjustmentId",
                  "nomisAdjustmentId": $nomisAdjustmentId,
                  "nomisAdjustmentCategory": "$nomisAdjustmentCategory",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                 },
                 "duplicate" : {
                  "adjustmentId": "$duplicateAdjustmentId",
                  "nomisAdjustmentId": $nomisAdjustmentId,
                  "nomisAdjustmentCategory": "$nomisAdjustmentCategory",
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45",
                  "mappingType": "MIGRATED"
                  }
              }
              }""",
    )

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

  fun verifyCreateMappingSentenceAdjustmentIds(nomsSentenceAdjustmentIds: Array<String>, times: Int = 1) = nomsSentenceAdjustmentIds.forEach {
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

  fun verifyCreateMappingAppointmentIds(nomisAppointmentIds: Array<String>, times: Int = 1) = nomisAppointmentIds.forEach {
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

  fun stubActivitiesMappingByMigrationId(
    whenCreated: String = "2020-01-01T11:10:00",
    count: Int = 7,
    migrationId: String = "2022-02-14T09:58:45",
    hasScheduleRules: Boolean = true,
  ) {
    fun aMigration(id: Int) = """{
      "nomisCourseActivityId": $id,
      "activityScheduleId": ${if (hasScheduleRules) 456 else null},
      "label": "$migrationId",
      "whenCreated": "$whenCreated"
    }"""

    val content = IntRange(1, count).joinToString { aMigration(it) }
    stubFor(
      get(urlPathMatching("/mapping/activities/migration/migration-id/.*")).willReturn(
        okJson(pageContent(content, count)),
      ),
    )
  }

  fun stubActivitiesMappingByMigrationIdFails(statusCode: Int) {
    stubFor(
      get(urlPathMatching("/mapping/activities/migration/migration-id/.*"))
        .willReturn(
          aResponse()
            .withStatus(statusCode)
            .withHeader("Content-Type", "application/json")
            .withBody("""{"userMessage":"Mappings error"}"""),
        ),
    )
  }

  fun verifyActivitiesMappingByMigrationId(migrationId: String, count: Int) {
    verify(
      getRequestedFor(
        urlPathEqualTo(
          "/mapping/activities/migration/migration-id/${
            URLEncoder.encode(
              migrationId,
              "UTF-8",
            )
          }",
        ),
      )
        .withQueryParam("size", equalTo("$count")),
    )
  }

  fun stubActivityMappingCountByMigrationId(count: Int = 7, includeIgnored: Boolean = false) {
    stubFor(
      get(urlPathMatching("/mapping/activities/migration-count/migration-id/.*"))
        .withQueryParam("includeIgnored", equalTo("$includeIgnored"))
        .willReturn(
          aResponse()
            .withStatus(HttpStatus.OK.value())
            .withHeader("Content-Type", "application/json")
            .withBody("$count"),

        ),
    )
  }

  fun stubActivityMappingCountByMigrationIdFails(statusCode: Int) {
    stubFor(
      get(urlPathMatching("/mapping/activities/migration-count/migration-id/.*"))
        .willReturn(
          aResponse()
            .withStatus(statusCode)
            .withHeader("Content-Type", "application/json")
            .withBody("""{"userMessage":"Some error"}"""),
        ),
    )
  }

  fun stubAllocationsMappingByMigrationId(whenCreated: String = "2020-01-01T11:10:00", count: Int = 7) {
    val content = """{
      "nomisAllocationId": 123,
      "activityAllocationId": 456,
      "activityScheduleId": 789,
      "label": "2022-02-14T09:58:45",
      "whenCreated": "$whenCreated"
    }"""
    stubFor(
      get(urlPathMatching("/mapping/allocations/migration/migration-id/.*")).willReturn(
        okJson(pageContent(content, count)),
      ),
    )
  }

  fun verifyCreateActivityMappings(count: Int, times: Int = 1) = repeat(count) { offset ->
    verify(
      times,
      postRequestedFor(urlPathEqualTo("/mapping/activities/migration")).withRequestBody(
        matchingJsonPath("nomisCourseActivityId", equalTo((offset + 1).toString())),
      ),
    )
  }

  fun verifyCreateAllocationMappings(count: Int, times: Int = 1) = repeat(count) { offset ->
    verify(
      times,
      postRequestedFor(urlPathEqualTo("/mapping/allocations/migration"))
        .withRequestBody(matchingJsonPath("nomisAllocationId", equalTo((offset + 1).toString()))),
    )
  }

  fun pageContent(content: String, count: Int) = """
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

  fun stubMappingCreateFailureFollowedBySuccess(url: String, method: (UrlPattern) -> MappingBuilder = WireMock::post) {
    stubFor(
      method(urlPathMatching(url))
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
      method(urlPathMatching(url))
        .inScenario("Retry create Scenario")
        .whenScenarioStateIs("Cause create Success")
        .willReturn(created())
        .willSetStateTo(STARTED),
    )
  }

  fun stubMappingUpdateFailureFollowedBySuccess(url: String) {
    stubFor(
      put(urlPathMatching(url))
        .inScenario("Retry update Scenario")
        .whenScenarioStateIs(STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause update Success"),
    )

    stubFor(
      put(urlPathMatching(url))
        .inScenario("Retry update Scenario")
        .whenScenarioStateIs("Cause update Success")
        .willReturn(aResponse().withStatus(200))
        .willSetStateTo(STARTED),
    )
  }

  fun stubMultipleGetActivityMappings(
    count: Int,
    activityScheduleId: Long = 4444,
    activityScheduleId2: Long? = 5555,
  ) {
    repeat(count) { offset ->
      stubFor(
        get(urlPathEqualTo("/mapping/activities/migration/nomis-course-activity-id/${1 + offset}"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(
                """{
                "nomisCourseActivityId": ${1 + offset},
                "activityId": ${activityScheduleId + offset},
                "activityId2": ${activityScheduleId2?.let { it + offset }},
                "label": "some old activity migration"
              }""",
              ),
          ),
      )
    }
  }

  fun stubActivityMappingCreateConflict(
    existingActivityId: Long = 457,
    duplicateActivityId: Long = 456,
    nomisCourseActivityId: Long = 123,
  ) {
    stubFor(
      post(urlPathEqualTo("/mapping/activities/migration"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "activityId": $existingActivityId,
                  "nomisCourseActivityId": $nomisCourseActivityId,
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45"
                 },
                 "duplicate" : {
                  "activityId": $duplicateActivityId,
                  "nomisCourseActivityId": $nomisCourseActivityId,
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45"
                  }
              }
              }""",
            ),
        ),
    )
  }

  fun stubAllocationMappingCreateConflict(
    existingAllocationId: Long,
    duplicateAllocationId: Long,
    nomisAllocationId: Long,
  ) {
    stubFor(
      post(urlPathEqualTo("/mapping/allocations/migration"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
              "moreInfo": 
              {
                "existing" :  {
                  "nomisAllocationId": $nomisAllocationId,
                  "activityAllocationId": $existingAllocationId,
                  "activityScheduleId": 123,
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45"
                 },
                 "duplicate" : {
                  "nomisAllocationId": $nomisAllocationId,
                  "activityAllocationId": $duplicateAllocationId,
                  "activityScheduleId": 123,
                  "label": "2022-02-14T09:58:45",
                  "whenCreated": "2022-02-14T09:58:45"
                  }
              }
              }""",
            ),
        ),
    )
  }

  fun verifyCreateMappingLocationIds(locationIds: Array<String>, times: Int = 1) = locationIds.forEach {
    verify(
      times,
      postRequestedFor(urlPathEqualTo("/mapping/locations")).withRequestBody(
        matchingJsonPath(
          "dpsLocationId",
          equalTo(it),
        ),
      ),
    )
  }

  fun stubLocationMappingCreateConflict(
    nomisLocationId: Long = 1234,
    existingLocationId: String = "4321",
    duplicateLocationId: String = "9876",
  ) {
    stubFor(
      post(urlPathEqualTo("/mapping/locations"))
        .willReturn(
          aResponse()
            .withStatus(409)
            .withHeader("Content-Type", "application/json")
            .withBody(
              """{
                "moreInfo": 
                {
                  "existing" :  {
                    "nomisLocationId": $nomisLocationId,
                    "dpsLocationId": "$existingLocationId",
                    "label": "2022-02-14T09:58:45",
                    "whenCreated": "2022-02-14T09:58:45",
                    "mappingType": "NOMIS_CREATED"
                   },
                   "duplicate" : {
                    "nomisLocationId": $nomisLocationId,
                    "dpsLocationId": "$duplicateLocationId",
                    "label": "2022-02-14T09:58:45",
                    "whenCreated": "2022-02-14T09:58:45",
                    "mappingType": "NOMIS_CREATED"
                  }
                }
              }""",
            ),
        ),
    )
  }

  fun stubGetLocation(dpsLocationId: String, nomisLocationId: Long) {
    val content = """{
      "dpsLocationId": "$dpsLocationId",
      "nomisLocationId": $nomisLocationId,   
      "label": "2022-02-14T09:58:45",
      "whenCreated": "2020-01-01T11:10:00",
      "mappingType": "NOMIS_CREATED"
    }"""
    stubFor(
      get(urlPathMatching("/mapping/locations/nomis/$nomisLocationId"))
        .willReturn(okJson(content)),
    )
  }

  fun stubGetAnyLocationNotFound() {
    stubFor(
      get(urlPathMatching("/mapping/locations/nomis/\\d"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value()),
        ),
    )
  }

  fun stubLocationsDeleteMapping(dpsLocationId: String) {
    stubFor(
      delete(urlPathMatching("/mapping/locations/dps/$dpsLocationId")).willReturn(
        aResponse().withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubLocationsDeleteMappingWithError(dpsLocationId: String, status: Int = 500) {
    stubFor(
      delete(urlPathMatching("/mapping/locations/dps/$dpsLocationId")).willReturn(
        aResponse().withStatus(status),
      ),
    )
  }

  fun stubGetApiLocationNomis(nomisLocationId: Long, dpsLocationId: String) {
    stubFor(
      get(urlPathMatching("/api/locations/nomis/$nomisLocationId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody("""{"dpsLocationId": "$dpsLocationId", "nomisLocationId": $nomisLocationId}"""),
        ),
    )
  }

  fun stubGetApiLocationNomis(nomisLocationId: Long, errorStatus: HttpStatus) {
    stubFor(
      get(urlPathMatching("/api/locations/nomis/$nomisLocationId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(errorStatus.value())
            .withBody("""{"userMessage": "some error"}"""),
        ),
    )
  }

  fun createMappingCount(url: String) = findAll(postRequestedFor(urlPathEqualTo(url))).count()
}
