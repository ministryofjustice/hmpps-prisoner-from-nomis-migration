package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.FindActiveActivityIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension.Companion.mappingApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.lang.Long.min

class NomisApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback,
  AfterEachCallback {
  companion object {
    const val VISITS_ID_URL = "/visits/ids"
    const val APPOINTMENTS_ID_URL = "/appointments/ids"
    const val ACTIVITIES_ID_URL = "/activities/ids"
    const val ALLOCATIONS_ID_URL = "/allocations/ids"
    const val COURT_SENTENCING_PRISONER_IDS = "/prisoners/ids/all"

    @JvmField
    val nomisApi = NomisApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    nomisApi.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    nomisApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    nomisApi.stop()
  }

  override fun afterEach(context: ExtensionContext) {
    mappingApi.setGlobalFixedDelay(0)
  }
}

class NomisApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8081
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse().withHeader("Content-Type", "application/json").withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubGetVisitsRoomUsage() {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/visits/rooms/usage-count"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(
              """
              [
    {
        "agencyInternalLocationDescription": "AGI-VISITS-OFF_VIS",
        "count": 95,
        "prisonId": "AGI"
    },
    {
        "agencyInternalLocationDescription": "BXI-VISITS-SOC_VIS",
        "count": 14314,
        "prisonId": "BXI"
    },
    {
        "agencyInternalLocationDescription": "AKI-VISITS-3RD SECTOR",
        "count": 390,
        "prisonId": "AKI"
    }]""",
            ),
        ),
    )
  }

  fun stubMultipleGetVisitsCounts(totalElements: Long, pageSize: Long) {
    // for each page create a response for each VisitId starting from 1 up to `totalElements`

    val pages = (totalElements / pageSize) + 1
    (0..pages).forEach { page ->
      val startVisitId = (page * pageSize) + 1
      val endVisitId = min((page * pageSize) + pageSize, totalElements)
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/visits/ids"),
        )
          .withQueryParam("page", equalTo(page.toString()))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(
                visitPagedResponse(
                  totalElements = totalElements,
                  visitIds = (startVisitId..endVisitId).map { it },
                  pageNumber = page,
                  pageSize = pageSize,
                ),
              ),
          ),
      )
    }
  }

  fun stubMultipleGetVisits(totalElements: Long) {
    (1..totalElements).forEach {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/visits/$it"),
        )
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(visitResponse(it)),
          ),
      )
    }
  }

  fun stubGetVisit(nomisVisitId: Long) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/visits/$nomisVisitId"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(visitResponse(nomisVisitId)),
        ),
    )
  }

  fun verifyGetVisit(nomisVisitId: Long) {
    nomisApi.verify(
      getRequestedFor(
        urlPathEqualTo("/visits/$nomisVisitId"),
      ),
    )
  }

  fun stubGetCancelledVisit(nomisVisitId: Long, modifyUserId: String = "transfer-staff-id") {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/visits/$nomisVisitId"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(visitCancelledResponse(nomisVisitId, modifyUserId)),
        ),
    )
  }

  fun verifyGetVisitsFilter(
    prisonIds: List<String>,
    visitTypes: List<String>,
    fromDateTime: String,
    toDateTime: String,
  ) {
    nomisApi.verify(
      getRequestedFor(
        urlPathEqualTo("/visits/ids"),
      )
        .withQueryParam("fromDateTime", equalTo(fromDateTime))
        .withQueryParam("toDateTime", equalTo(toDateTime)),
    )
    // verify each parameter one at a time
    prisonIds.forEach {
      nomisApi.verify(
        getRequestedFor(
          urlPathEqualTo("/visits/ids"),
        )
          .withQueryParam("prisonIds", equalTo(it)),
      )
    }
    visitTypes.forEach {
      nomisApi.verify(
        getRequestedFor(
          urlPathEqualTo("/visits/ids"),
        )
          .withQueryParam("visitTypes", equalTo(it)),
      )
    }
  }

  // //////////////////////////////////// Locations //////////////////////////////////////
  fun stubGetLocation(nomisLocationId: Long = 1234, parentLocationId: Long = 5678) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/locations/$nomisLocationId"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(locationResponse(nomisLocationId, parentLocationId)),
        ),
    )
  }

  fun stubGetLocationWithMinimalData(nomisLocationId: Long = 1234) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/locations/$nomisLocationId"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(basicLocationResponse(nomisLocationId)),
        ),
    )
  }

  fun stubGetLocationNotFound(nomisLocationId: Long = 1234) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/locations/$nomisLocationId"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value()),
        ),
    )
  }

  fun stubMultipleGetAppointmentIdCounts(totalElements: Long, pageSize: Long) {
    // for each page create a response for each id starting from 1 up to `totalElements`

    val pages = (totalElements / pageSize) + 1
    (0..pages).forEach { page ->
      val startId = (page * pageSize) + 1
      val endId = min((page * pageSize) + pageSize, totalElements)
      nomisApi.stubFor(
        get(urlPathEqualTo("/appointments/ids"))
          .withQueryParam("page", equalTo(page.toString()))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(
                appointmentIdsPagedResponse(
                  totalElements = totalElements,
                  ids = (startId..endId).map { it },
                  pageNumber = page,
                  pageSize = pageSize,
                ),
              ),
          ),
      )
    }
  }

  fun stubMultipleGetAppointments(intProgression: IntProgression) {
    (intProgression).forEach {
      nomisApi.stubFor(
        get(urlPathEqualTo("/appointments/$it"))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(appointmentResponse(it.toLong())),
          ),
      )
    }
  }

  fun stubMultipleGetActivitiesIdCounts(totalElements: Long, pageSize: Long, hasScheduleRules: Boolean = true) {
    // for each page create a response for each id starting from 1 up to `totalElements`

    val pages = (totalElements / pageSize) + 1
    (0..pages).forEach { page ->
      val startId = (page * pageSize) + 1
      val endId = min((page * pageSize) + pageSize, totalElements)
      nomisApi.stubFor(
        get(urlPathEqualTo("/activities/ids"))
          .withQueryParam("page", equalTo(page.toString()))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(
                activitiesIdsPagedResponse(
                  totalElements = totalElements,
                  ids = (startId..endId).map { FindActiveActivityIdsResponse(it, hasScheduleRules) },
                  pageNumber = page,
                  pageSize = pageSize,
                ),
              ),
          ),
      )
    }
  }

  fun stubMultipleGetAllocationsIdCounts(totalElements: Long, pageSize: Long) {
    // for each page create a response for each id starting from 1 up to `totalElements`

    val pages = (totalElements / pageSize) + 1
    (0..pages).forEach { page ->
      val startId = (page * pageSize) + 1
      val endId = min((page * pageSize) + pageSize, totalElements)
      nomisApi.stubFor(
        get(urlPathEqualTo("/allocations/ids"))
          .withQueryParam("page", equalTo(page.toString()))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(
                allocationsIdsPagedResponse(
                  totalElements = totalElements,
                  ids = (startId..endId).map { it },
                  pageNumber = page,
                  pageSize = pageSize,
                ),
              ),
          ),
      )
    }
  }

  fun verifyActivitiesGetIds(
    url: String,
    prisonId: String,
    courseActivityId: Long? = null,
  ) {
    val request = getRequestedFor(
      urlPathEqualTo(url),
    )
      .withQueryParam("prisonId", equalTo(prisonId))
      .apply { courseActivityId?.run { withQueryParam("courseActivityId", equalTo(courseActivityId.toString())) } }
    nomisApi.verify(
      request,
    )
  }

  fun stubMultipleGetActivities(count: Int) {
    repeat(count) { offset ->
      nomisApi.stubFor(
        get(urlPathEqualTo("/activities/${1 + offset}"))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(activitiesResponse((1 + offset).toLong())),
          ),
      )
    }
  }

  fun stubEndActivities() {
    nomisApi.stubFor(
      put(urlPathEqualTo("/activities/end"))
        .willReturn(
          aResponse()
            .withStatus(HttpStatus.OK.value()),
        ),
    )
  }

  fun stubEndActivitiesError(status: HttpStatus) {
    nomisApi.stubFor(
      put(urlPathEqualTo("/activities/end"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody("""{"status":"${status.value()}",userMessage = "NOMIS error"}"""),
        ),
    )
  }

  fun verifyEndActivities(expectedCourseActivityIds: String, endDate: String, times: Int = 1) {
    nomisApi.verify(
      times,
      putRequestedFor(urlPathEqualTo("/activities/end"))
        .withRequestBody(containing(""""courseActivityIds":$expectedCourseActivityIds,"endDate":"$endDate"""")),
    )
  }

  fun stubMoveActivitiesEndDate() {
    nomisApi.stubFor(
      put(urlPathEqualTo("/activities/move-end-date"))
        .willReturn(
          aResponse()
            .withStatus(HttpStatus.OK.value()),
        ),
    )
  }

  fun stubMoveActivitiesEndDateError(status: HttpStatus) {
    nomisApi.stubFor(
      put(urlPathEqualTo("/activities/move-end-date"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody("""{"status":"${status.value()}",userMessage = "NOMIS error"}"""),
        ),
    )
  }

  fun verifyMoveActivitiesEndDate(expectedCourseActivityIds: String, oldEndDate: String, newEndDate: String, times: Int = 1) {
    nomisApi.verify(
      times,
      putRequestedFor(urlPathEqualTo("/activities/move-end-date"))
        .withRequestBody(containing(""""courseActivityIds":$expectedCourseActivityIds,"oldEndDate":"$oldEndDate","newEndDate":"$newEndDate"""")),
    )
  }

  fun stubMultipleGetAllocations(count: Int) {
    repeat(count) { offset ->
      nomisApi.stubFor(
        get(urlPathEqualTo("/allocations/${1 + offset}"))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(allocationsResponse((1 + offset).toLong())),
          ),
      )
    }
  }

  fun stubGetInitialCount(
    urlPath: String,
    totalElements: Long,
    pagedResponse: (totalElements: Long) -> String,
  ) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo(urlPath),
      )
        .withQueryParam("page", equalTo("0"))
        .withQueryParam("size", equalTo("1"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(pagedResponse(totalElements)),
        ),
    )
  }

  fun verifyGetIdsCount(url: String, fromDate: String, toDate: String, prisonId: String? = null) {
    val request = getRequestedFor(
      urlPathEqualTo(url),
    )
      .withQueryParam("fromDate", equalTo(fromDate))
      .withQueryParam("toDate", equalTo(toDate))
    prisonId?.let { request.withQueryParam("prisonIds", equalTo(prisonId)) }
    nomisApi.verify(
      request,
    )
  }

  fun stubGetPrisonerIds(totalElements: Long = 20, pageSize: Long = 20, firstOffenderNo: String = "A0001KT") {
    val content: List<PrisonerId> = (1..kotlin.math.min(pageSize, totalElements)).map {
      PrisonerId(
        offenderNo = firstOffenderNo.replace("0001", "$it".padStart(4, '0')),
      )
    }
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/ids/all")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            pageContent(
              jsonMapper = jsonMapper,
              content = content,
              pageSize = pageSize,
              pageNumber = 0,
              totalElements = totalElements,
              size = pageSize.toInt(),
            ),
          ),
      ),
    )
  }

  fun stubGetPrisonerDetails(offenderNo: String, details: PrisonerDetails) {
    stubFor(
      get("/prisoners/$offenderNo")
        .willReturn(okJson(jsonMapper.writeValueAsString(details))),
    )
  }

  fun stubCheckServiceAgencyForPrisoner(serviceCode: String = "VISIT_ALLOCATION", prisonNumber: String = "A1234BC") {
    nomisApi.stubFor(
      get(urlPathEqualTo("/agency-switches/$serviceCode/prisoner/$prisonNumber")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCheckServiceAgencyForPrisonerNotFound(serviceCode: String = "VISIT_ALLOCATION", prisonNumber: String = "A1234BC") {
    nomisApi.stubFor(
      get(urlPathEqualTo("/agency-switches/$serviceCode/prisoner/$prisonNumber")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NOT_FOUND.value()),
      ),
    )
  }

  fun stubCheckAgencySwitchForAgency(serviceCode: String = "VISIT_ALLOCATION", agencyId: String = "MDI") {
    stubFor(
      get(urlPathEqualTo("/agency-switches/$serviceCode/agency/$agencyId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }
  fun stubCheckAgencySwitchForAgencyNotFound(serviceCode: String = "VISIT_ALLOCATION", agencyId: String = "MDI") {
    stubFor(
      get(urlPathEqualTo("/agency-switches/$serviceCode/agency/$agencyId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NOT_FOUND.value()),
      ),
    )
  }
}

private fun visitResponse(visitId: Long) = """
              {
              "visitId": $visitId,
              "offenderNo": "A7948DY",
              "startDateTime": "2021-10-25T09:00:00",
              "endDateTime": "2021-10-25T11:45:00",
              "createUserId": "user1",
              "whenCreated": "2021-10-24T09:00:00",
              "prisonId": "MDI",
              "visitors": [
                    {
                        "personId": 4729570,
                        "leadVisitor": true
                    },
                    {
                        "personId": 4729580,
                        "leadVisitor": false
                    }
                ],
                "visitType": {
                    "code": "SCON",
                    "description": "Social Contact"
                },
                "visitStatus": {
                    "code": "SCH",
                    "description": "Scheduled"
                },
                "agencyInternalLocation": {
                    "code": "OFF_VIS",
                    "description": "MDI-VISITS-OFF_VIS"
                },
                "commentText": "Not sure if this is the right place to be"
              }
            """

private fun visitCancelledResponse(visitId: Long, modifyUserId: String) = """
              {
              "visitId": $visitId,
              "offenderNo": "A7948DY",
              "startDateTime": "2021-10-25T09:00:00",
              "endDateTime": "2021-10-25T11:45:00",
              "whenCreated": "2021-10-24T09:00:00",
              "whenUpdated": "2021-10-25T14:45:00",
              "prisonId": "MDI",
              "createUserId": "user1",
              "modifyUserId": "$modifyUserId",
              "visitors": [
                    {
                        "personId": 4729570,
                        "leadVisitor": true
                    },
                    {
                        "personId": 4729580,
                        "leadVisitor": false
                    }
                ],
                "visitType": {
                    "code": "SCON",
                    "description": "Social Contact"
                },
                "visitStatus": {
                    "code": "CANC",
                    "description": "Cancelled"
                },
                "visitOutcome": {
                    "code": "OFFCANC",
                    "description": "Cancelled"
                },
                "agencyInternalLocation": {
                    "code": "OFF_VIS",
                    "description": "MDI-VISITS-OFF_VIS"
                },
                "commentText": "Not sure if this is the right place to be"
              }
            """

fun visitPagedResponse(
  totalElements: Long = 10,
  visitIds: List<Long> = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
  pageSize: Long = 10,
  pageNumber: Long = 0,
): String {
  val content = visitIds.map { """{ "visitId": $it }""" }.joinToString { it }
  return pageContent(content, pageSize, pageNumber, totalElements, visitIds.size)
}

fun appointmentIdsPagedResponse(
  totalElements: Long = 10,
  ids: List<Long> = (0L..10L).toList(),
  pageSize: Long = 10,
  pageNumber: Long = 0,
): String {
  val content = ids.map { """{ "eventId": $it }""" }.joinToString { it }
  return pageContent(content, pageSize, pageNumber, totalElements, ids.size)
}

fun activitiesIdsPagedResponse(
  totalElements: Long = 10,
  ids: List<FindActiveActivityIdsResponse> = (0L..10L).map { FindActiveActivityIdsResponse(it, true) },
  pageSize: Long = 10,
  pageNumber: Long = 0,
): String {
  val content = ids.map { """{ "courseActivityId": ${it.courseActivityId}, "hasScheduleRules": ${it.hasScheduleRules} }""" }.joinToString { it }
  return pageContent(content, pageSize, pageNumber, totalElements, ids.size)
}

fun allocationsIdsPagedResponse(
  totalElements: Long = 10,
  ids: List<Long> = (0L..10L).toList(),
  pageSize: Long = 10,
  pageNumber: Long = 0,
): String {
  val content = ids.map { """{ "allocationId": $it }""" }.joinToString { it }
  return pageContent(content, pageSize, pageNumber, totalElements, ids.size)
}

private fun locationResponse(id: Long = 1234, parentLocationId: Long = 5678): String =
  """
  {
    "locationId": $id,
    "locationCode": "001",
    "locationType": "CELL",
    "description": "MDI-1-1-001",
    "prisonId": "MDI",
    "operationalCapacity": 1,
    "userDescription": "MDI-1-1-001",
    "certified": true,
    "active": true,
    "createDatetime": "2023-01-01T11:00:01.234567",
    "createUsername": "ITAG1",
    "modifyUsername": "ITAG2",
    "parentLocationId": $parentLocationId,
    "capacity": 1,
    "cnaCapacity": 1,
    "listSequence": 1,
    "comment": "A comment",
    "unitType": "NA",
    "tracking": true,
    "profiles": [
      {
        "profileType": "HOU_SANI_FIT",
        "profileCode": "ACB"
      }
    ],
    "usages": [
      {
        "internalLocationUsageType": "MOVEMENT",
        "usageLocationType": "FAIT",
        "capacity": 2,
        "sequence": 3
      }
    ]
  }
  """.trimIndent()

private fun basicLocationResponse(id: Long = 1234): String =
  """
  {
    "locationId": $id,
    "locationCode": "001",
    "locationType": "CELL",
    "description": "MDI-1-1-001",
    "prisonId": "MDI",
    "certified": true,
    "active": true,
    "createDatetime": "2023-01-01T11:00:01.234567",
    "createUsername": "ITAG1",
    "tracking": false
  }
  """.trimIndent()

private fun appointmentResponse(
  bookingId: Long = 2,
  offenderNo: String = "G4803UT",
): String =
  """
{
  "bookingId":$bookingId,
  "offenderNo": "$offenderNo",
  "prisonId": "LEI",
  "internalLocation": 23456,
  "startDateTime":"2022-10-06T09:30:00",
  "endDateTime":"2022-10-06T09:50:00",
  "comment":"a comment",
  "subtype":"VLB",
  "status":"SCH",
  "createdBy": "ITAG1",
  "createdDate": "2023-01-01T11:00:01.234567",
  "modifiedBy": "ITAG2",
  "modifiedDate": "2023-02-02T12:00:03.777666"
}
  """.trimIndent()

private fun activitiesResponse(
  courseActivityId: Long = 123,
): String =
  """
{
  "courseActivityId": $courseActivityId,
  "programCode": "INDUCTION",
  "prisonId": "BXI",
  "startDate": "2020-04-11",
  "endDate": "2023-11-15",
  "internalLocationId": 1234,
  "internalLocationCode": "KITCH",
  "internalLocationDescription": "RSI-WORK_IND-KITCH",
  "capacity": 10,
  "description": "Kitchen work",
  "minimumIncentiveLevel": "BAS",
  "excludeBankHolidays": false,
  "payPerSession": "H",
  "outsideWork": false,
  "scheduleRules": [
    {
      "startTime": "09:00",
      "endTime": "11:00",
      "monday": true,
      "tuesday": true,
      "wednesday": true,
      "thursday": true,
      "friday": true,
      "saturday": true,
      "sunday": true,
      "slotCategoryCode": "AM"
    },
    {
      "startTime": "13:00",
      "endTime": "15:30",
      "monday": true,
      "tuesday": true,
      "wednesday": true,
      "thursday": true,
      "friday": true,
      "saturday": false,
      "sunday": false,
      "slotCategoryCode": "PM"
    }
  ],
  "payRates": [
    {
      "incentiveLevelCode": "BAS",
      "payBand": "1",
      "rate": 3.2
    },    
    {
      "incentiveLevelCode": "BAS",
      "payBand": "2",
      "rate": 3.4
    }
  ]
}
  """.trimIndent()

private fun allocationsResponse(
  courseActivityId: Long = 123,
): String =
  """
{
    "prisonId": "BXI",
    "courseActivityId": $courseActivityId,
    "nomisId": "A1234AA",
    "bookingId": 4321,
    "startDate": "2020-04-11",
    "endDate": "2023-11-15",
    "suspended": false,
    "endComment": "Ended",
    "endReasonCode": "WDRAWN",
    "payBand": "1",
    "livingUnitDescription": "BXI-A-1-01",
    "exclusions": [{ "day": "MON", "slot": "AM" }, { "day": "TUE", "slot": "ED" }],
    "scheduleRules": [
        {
            "startTime": "09:00",
            "endTime": "11:00",
            "monday": true,
            "tuesday": true,
            "wednesday": true,
            "thursday": true,
            "friday": true,
            "saturday": true,
            "sunday": true,
            "slotCategoryCode": "AM"
        },
        {
            "startTime": "13:00",
            "endTime": "15:30",
            "monday": true,
            "tuesday": true,
            "wednesday": true,
            "thursday": true,
            "friday": true,
            "saturday": false,
            "sunday": false,
            "slotCategoryCode": "PM"
        }
    ],
    "activityStartDate": "2020-04-11"
}
  """.trimIndent()
