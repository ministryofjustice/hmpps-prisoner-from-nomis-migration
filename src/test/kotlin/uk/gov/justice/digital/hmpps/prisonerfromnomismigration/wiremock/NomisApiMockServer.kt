package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.lang.Long.min

class NomisApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    const val ADJUDICATIONS_ID_URL = "/adjudications/charges/ids"
    const val VISITS_ID_URL = "/visits/ids"
    const val APPOINTMENTS_ID_URL = "/appointments/ids"
    const val ADJUSTMENTS_ID_URL = "/adjustments/ids"
    const val ACTIVITIES_ID_URL = "/activities/ids"
    const val ALLOCATIONS_ID_URL = "/allocations/ids"
    const val INCIDENTS_ID_URL = "/incidents/ids"
    const val LOCATIONS_ID_URL = "/locations/ids"

    @JvmField
    val nomisApi = NomisApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    nomisApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    nomisApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    nomisApi.stop()
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

  fun stubMultipleGetAdjudicationIdCounts(
    totalElements: Long,
    pageSize: Long,
    pagedResponse: (() -> String)? = null,
  ) {
    // for each page create a response for each adjudication id starting from 1 up to `totalElements`

    val pages = (totalElements / pageSize) + 1
    (0..pages).forEach { page ->
      val startId = (page * pageSize) + 1
      val endId = min((page * pageSize) + pageSize, totalElements)
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/adjudications/charges/ids"),
        )
          .withQueryParam("page", equalTo(page.toString()))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(
                pagedResponse?.let { pagedResponse() } ?: adjudicationsIdsPagedResponse(
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

  fun stubGetSingleAdjudicationId(adjudicationNumber: Long) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/adjudications/charges/ids"),
      )
        .withQueryParam("page", equalTo("0"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(
              adjudicationsIdsPagedResponse(
                totalElements = 1,
                ids = listOf(adjudicationNumber),
                pageNumber = 1,
                pageSize = 10,
              ),
            ),
        ),
    )
  }

  fun stubMultipleGetAdjudications(intProgression: IntProgression) {
    (intProgression).forEach {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/adjudications/adjudication-number/$it/charge-sequence/1"),
        )
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(adjudicationResponse(adjudicationNumber = it.toLong())),
          ),
      )
    }
  }

  fun stubGetAdjudication(
    adjudicationNumber: Long,
    chargeSequence: Int = 1,
    adjudicationResponse: (() -> String)? = null,
  ) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/adjudications/adjudication-number/$adjudicationNumber/charge-sequence/$chargeSequence"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              adjudicationResponse?.let { adjudicationResponse() } ?: adjudicationResponse(
                adjudicationNumber = 654321,
                chargeSequence = chargeSequence,
              ),
            ),
        ),
    )
  }

  fun stubMultipleGetAdjustmentIdCounts(totalElements: Long, pageSize: Long) {
    // for each page create a response for each sentence adjustment id starting from 1 up to `totalElements`

    val pages = (totalElements / pageSize) + 1
    (0..pages).forEach { page ->
      val startSentenceAdjustmentId = (page * pageSize) + 1
      val endBSentenceAdjustmentId = min((page * pageSize) + pageSize, totalElements)
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/adjustments/ids"),
        )
          .withQueryParam("page", equalTo(page.toString()))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(
                adjustmentIdsPagedResponse(
                  totalElements = totalElements,
                  adjustmentIds = (startSentenceAdjustmentId..endBSentenceAdjustmentId).map { it },
                  pageNumber = page,
                  pageSize = pageSize,
                ),
              ),
          ),
      )
    }
  }

  fun stubMultipleGetSentenceAdjustments(intProgression: IntProgression) {
    (intProgression).forEach {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/sentence-adjustments/$it"),
        )
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(sentenceAdjustmentResponse(it.toLong())),
          ),
      )
    }
  }

  // //////////////////////////////////// Incidents //////////////////////////////////////

  fun stubMultipleGetIncidentIdCounts(totalElements: Long, pageSize: Long) {
    // for each page create a response for each incident id starting from 1 up to `totalElements`

    val pages = (totalElements / pageSize) + 1
    (0..pages).forEach { page ->
      val startIncidentId = (page * pageSize) + 1
      val endIncidentId = min((page * pageSize) + pageSize, totalElements)
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/incidents/ids"),
        )
          .withQueryParam("page", equalTo(page.toString()))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(
                incidentIdsPagedResponse(
                  totalElements = totalElements,
                  ids = (startIncidentId..endIncidentId).map { it },
                  pageNumber = page,
                  pageSize = pageSize,
                ),
              ),
          ),
      )
    }
  }

  fun stubGetIncident(nomisIncidentId: Long = 1234) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/incidents/$nomisIncidentId"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(incidentResponse(nomisIncidentId)),
        ),
    )
  }

  fun stubGetIncidentNotFound(nomisIncidentId: Long = 1234) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/incidents/$nomisIncidentId"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value()),
        ),
    )
  }

  fun stubMultipleGetIncidents(intProgression: IntProgression) {
    (intProgression).forEach {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/incidents/$it"),
        )
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(incidentResponse(it.toLong())),
          ),
      )
    }
  }

  // //////////////////////////////////// Locations //////////////////////////////////////

  fun stubMultipleGetLocationIdCounts(totalElements: Long, pageSize: Long) {
    // for each page create a response for each location id starting from 1 up to `totalElements`

    val pages = (totalElements / pageSize) + 1
    (0..pages).forEach { page ->
      val startLocationId = (page * pageSize) + 1
      val endLocationId = min((page * pageSize) + pageSize, totalElements)
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/locations/ids"),
        )
          .withQueryParam("page", equalTo(page.toString()))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(
                locationIdsPagedResponse(
                  totalElements = totalElements,
                  ids = (startLocationId..endLocationId).map { it },
                  pageNumber = page,
                  pageSize = pageSize,
                ),
              ),
          ),
      )
    }
  }

  fun stubGetLocation(nomisLocationId: Long = 1234) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/locations/$nomisLocationId"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(locationResponse(nomisLocationId)),
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

  fun stubMultipleGetLocations(intProgression: IntProgression) {
    (intProgression).forEach {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/locations/$it"),
        )
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(locationResponse(it.toLong())),
          ),
      )
    }
  }

  // //////////////////////////////////// Sentencing //////////////////////////////////////

  fun stubMultipleGetKeyDateAdjustments(intProgression: IntProgression) {
    (intProgression).forEach {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/key-date-adjustments/$it"),
        )
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(keyDateAdjustmentResponse(it.toLong())),
          ),
      )
    }
  }

  fun stubGetSentenceAdjustment(adjustmentId: Long, hiddenForUsers: Boolean = false) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/sentence-adjustments/$adjustmentId"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              sentenceAdjustmentResponse(sentenceAdjustmentId = adjustmentId, hiddenForUsers = hiddenForUsers),
            ),
        ),
    )
  }

  fun stubGetKeyDateAdjustment(adjustmentId: Long) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/key-date-adjustments/$adjustmentId"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(keyDateAdjustmentResponse(keyDateAdjustmentId = adjustmentId)),
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

  fun stubMultipleGetActivitiesIdCounts(totalElements: Long, pageSize: Long) {
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
                  ids = (startId..endId).map { it },
                  pageNumber = page,
                  pageSize = pageSize,
                ),
              ),
          ),
      )
    }
  }

  fun stubGetActivitiesIdCountsError(badPrison: String) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/activities/ids"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.BAD_REQUEST.value())
            .withBody(
              """{
                "status": 400,
                "userMessage": "Bad request: Prison with id=$badPrison does not exist"
              }
              """.trimIndent(),

            ),
        ),
    )
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

  fun stubGetAllocationsIdCountsError(badPrison: String) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/allocations/ids"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.BAD_REQUEST.value())
            .withBody(
              """{
                "status": 400,
                "userMessage": "Bad request: Prison with id=$badPrison does not exist"
              }
              """.trimIndent(),

            ),
        ),
    )
  }

  fun verifyActivitiesGetIds(url: String, prisonId: String, excludeProgramCodes: List<String> = listOf(), courseActivityId: Long? = null) {
    val request = getRequestedFor(
      urlPathEqualTo(url),
    )
      .withQueryParam("prisonId", equalTo(prisonId))
      .apply { excludeProgramCodes.forEach { withQueryParam("excludeProgramCode", equalTo(it)) } }
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

  fun verifyEndActivities(expectedCourseActivityIds: List<Long>) {
    putRequestedFor(urlPathEqualTo("/activities/end"))
      .withRequestBody(containing(""""courseActivityIds": $expectedCourseActivityIds"""))
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

  fun stubGetSuspendedAllocations() {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/allocations/suspended"),
      )
        .withQueryParam("prisonId", equalTo("MDI"))
        .withQueryParam("excludeProgramCode", equalTo("SAA_EDUCATION"))
        .withQueryParam("excludeProgramCode", equalTo("SAA_INDUCTION"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(suspendedAllocationsResponse()),
        ),
    )
  }

  fun verifyGetSuspendedAllocations() {
    nomisApi.verify(
      getRequestedFor(
        urlPathEqualTo("/allocations/suspended"),
      )
        .withQueryParam("prisonId", equalTo("MDI"))
        .withQueryParam("excludeProgramCode", equalTo("SAA_EDUCATION"))
        .withQueryParam("excludeProgramCode", equalTo("SAA_INDUCTION")),
    )
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

fun adjustmentIdsPagedResponse(
  totalElements: Long = 10,
  adjustmentIds: List<Long> = (0L..10L).toList(),
  pageSize: Long = 10,
  pageNumber: Long = 0,
): String {
  val content = adjustmentIds.map { """{ "adjustmentId": $it, "adjustmentCategory": "${getAdjustmentCategory(it)}"}""" }
    .joinToString { it }
  return pageContent(content, pageSize, pageNumber, totalElements, adjustmentIds.size)
}

fun incidentIdsPagedResponse(
  totalElements: Long = 10,
  ids: List<Long> = (0L..10L).toList(),
  pageSize: Long = 10,
  pageNumber: Long = 0,
): String {
  val content = ids.map { """{ "incidentId": $it }""" }.joinToString { it }
  return pageContent(content, pageSize, pageNumber, totalElements, ids.size)
}

fun locationIdsPagedResponse(
  totalElements: Long = 10,
  ids: List<Long> = (0L..10L).toList(),
  pageSize: Long = 10,
  pageNumber: Long = 0,
): String {
  val content = ids.map { """{ "locationId": $it }""" }.joinToString { it }
  return pageContent(content, pageSize, pageNumber, totalElements, ids.size)
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
  ids: List<Long> = (0L..10L).toList(),
  pageSize: Long = 10,
  pageNumber: Long = 0,
): String {
  val content = ids.map { """{ "courseActivityId": $it }""" }.joinToString { it }
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

fun adjudicationsIdsPagedResponse(
  totalElements: Long = 10,
  ids: List<Long> = (0L..10L).toList(),
  pageSize: Long = 10,
  pageNumber: Long = 0,
): String {
  val content = ids.map {
    // language=json
    """{ "adjudicationNumber": $it, "chargeSequence": 1, "offenderNo": "AD12345" }"""
  }.joinToString { it }
  return pageContent(content, pageSize, pageNumber, totalElements, ids.size)
}

fun adjudicationsIdsPagedResponse(
  adjudicationNumber: Long,
  chargeSequence: Int,
  offenderNo: String,
): String {
  return pageContent(
    """{ "adjudicationNumber": $adjudicationNumber, "chargeSequence": $chargeSequence, "offenderNo": "$offenderNo" }""",
    pageSize = 10,
    pageNumber = 0,
    totalElements = 1,
    size = 10,
  )
}

private fun pageContent(
  content: String,
  pageSize: Long,
  pageNumber: Long,
  totalElements: Long,
  size: Int,
) =
  // language=json
  """
{
    "content": [
        $content
    ],
    "pageable": {
        "sort": {
            "empty": false,
            "sorted": true,
            "unsorted": false
        },
        "offset": 0,
        "pageSize": $pageSize,
        "pageNumber": $pageNumber,
        "paged": true,
        "unpaged": false
    },
    "last": false,
    "totalPages": ${totalElements / pageSize + 1},
    "totalElements": $totalElements,
    "size": $pageSize,
    "number": $pageNumber,
    "sort": {
        "empty": false,
        "sorted": true,
        "unsorted": false
    },
    "first": true,
    "numberOfElements": $size,
    "empty": false
}
  """.trimIndent()

private fun getAdjustmentCategory(it: Long) = if (it % 2L == 0L) "KEY_DATE" else "SENTENCE"

private fun sentenceAdjustmentResponse(
  bookingId: Long = 2,
  offenderNo: String = "G4803UT",
  sentenceAdjustmentId: Long = 3,
  hiddenForUsers: Boolean = false,
): String {
  // language=JSON
  return """
{
  "bookingId":$bookingId,
  "id":$sentenceAdjustmentId,
  "offenderNo": "$offenderNo",
  "sentenceSequence": 0,
  "commentText":"a comment",
  "adjustmentDate":"2021-10-06",
  "adjustmentFromDate":"2021-10-07",
  "active":true,
  "hiddenFromUsers":$hiddenForUsers,
  "adjustmentDays":8,
  "adjustmentType": {
    "code": "RST",
    "description": "RST Desc"
  },
  "hasBeenReleased": false
  }
   
  """.trimIndent()
}

private fun keyDateAdjustmentResponse(
  bookingId: Long = 2,
  keyDateAdjustmentId: Long = 3,
  offenderNo: String = "G4803UT",
): String {
  // language=JSON
  return """
{
  "bookingId":$bookingId,
  "id":$keyDateAdjustmentId,
  "offenderNo": "$offenderNo",
  "commentText":"a comment",
  "adjustmentDate":"2021-10-06",
  "adjustmentFromDate":"2021-10-07",
  "active":true,
  "adjustmentDays":8,
  "adjustmentType": {
    "code": "ADA",
    "description": "Additional days"
  },
  "hasBeenReleased": false
  }
   
  """.trimIndent()
}

fun adjudicationResponse(
  offenderNo: String = "G4803UT",
  adjudicationNumber: Long = 3,
  chargeSequence: Int = 1,
): String {
  // language=json
  return """
{
    "adjudicationSequence": 1,
    "offenderNo": "$offenderNo",
    "bookingId": 1201725,
    "hasMultipleCharges": false,
    "adjudicationNumber": $adjudicationNumber,
    "gender": {
        "code": "M",
        "description": "Male"
    },
    "currentPrison": {
        "code": "BXI",
        "description": "BRIXTON (HMP)"
    },
    "partyAddedDate": "2023-08-07",
    "incident": {
        "adjudicationIncidentId": 1503234,
        "reportingStaff": {
            "username": "AMARKE_GEN",
            "staffId": 485887,
            "firstName": "ANDY",
            "lastName": "MARKE",
            "createdByUsername": "AMARKE_GEN"
        },
        "incidentDate": "2023-08-07",
        "incidentTime": "11:23:00",
        "reportedDate": "2023-08-07",
        "reportedTime": "11:20:00",
        "createdByUsername": "AMARKE_GEN",
        "createdDateTime": "2023-08-07T11:24:10.501959",
        "internalLocation": {
            "locationId": 172315,
            "code": "W/SHOP13",
            "description": "WWI-EDUC-W/SHOP13"
        },
        "incidentType": {
            "code": "GOV",
            "description": "Governor's Report"
        },
        "details": "some incident",
        "prison": {
            "code": "WWI",
            "description": "WANDSWORTH (HMP)"
        },
        "prisonerWitnesses": [],
        "prisonerVictims": [],
        "otherPrisonersInvolved": [],
        "reportingOfficers": [],
        "staffWitnesses": [],
        "staffVictims": [],
        "otherStaffInvolved": [],
        "repairs": [
            {
                "type": {
                    "code": "ELEC",
                    "description": "Electrical"
                },
                "createdByUsername": "AMARKE_GEN"
            },
            {
                "type": {
                    "code": "PLUM",
                    "description": "Plumbing"
                },
                "comment": "plum",
                "createdByUsername": "AMARKE_GEN"
            },
            {
                "type": {
                    "code": "DECO",
                    "description": "Re-Decoration"
                },
                "comment": "dec",
                "createdByUsername": "AMARKE_GEN"
            },
            {
                "type": {
                    "code": "FABR",
                    "description": "Fabric"
                },
                "comment": "fab",
                "createdByUsername": "AMARKE_GEN"
            },
            {
                "type": {
                    "code": "CLEA",
                    "description": "Cleaning"
                },
                "comment": "clea",
                "createdByUsername": "AMARKE_GEN"
            },
            {
                "type": {
                    "code": "LOCK",
                    "description": "Lock"
                },
                "comment": "lock",
                "createdByUsername": "AMARKE_GEN"
            }
        ]
    },
    "charge": {
        "offence": {
            "code": "51:2D",
            "description": "Detains any person against his will - detention against will of staff (not prison offr)",
            "type": {
                "code": "51",
                "description": "Prison Rule 51"
            }
        },
        "offenceId": "1525933/2",
        "chargeSequence": $chargeSequence
    },
    "investigations": [
        {
            "investigator": {
                "username": "KQG94Y",
                "staffId": 67362,
                "firstName": "EKSINRN",
                "lastName": "AALYLE",
                "createdByUsername": "AMARKE_GEN"
            },
            "comment": "comment one",
            "dateAssigned": "2023-08-07",
            "evidence": [
                {
                    "type": {
                        "code": "EVI_BAG",
                        "description": "Evidence Bag"
                    },
                    "date": "2023-08-07",
                    "detail": "evidence bag",
                    "createdByUsername": "AMARKE_GEN"
                },
                {
                    "type": {
                        "code": "OTHER",
                        "description": "Other"
                    },
                    "date": "2023-08-07",
                    "detail": "other stuff",
                    "createdByUsername": "AMARKE_GEN"
                }
            ]
        },
        {
            "investigator": {
                "username": "HQZ33B",
                "staffId": 67839,
                "firstName": "DIKBLISNG",
                "lastName": "ABBOY",
                "createdByUsername": "AMARKE_GEN"
            },
            "comment": "another comment",
            "dateAssigned": "2023-08-07",
            "evidence": [
                {
                    "type": {
                        "code": "BEHAV",
                        "description": "Behaviour Report"
                    },
                    "date": "2023-08-07",
                    "detail": "report",
                    "createdByUsername": "AMARKE_GEN"
                }
            ]
        }
    ],
    "hearings": [
        {
            "hearingId": 2012708,
            "type": {
                "code": "GOV_ADULT",
                "description": "Governor's Hearing Adult"
            },
            "hearingDate": "2023-08-07",
            "hearingTime": "16:55:00",
            "internalLocation": {
                "locationId": 176776,
                "code": "ADJR",
                "description": "WWI-RES-CSU-ADJR"
            },
            "eventStatus": {
                "code": "EXP",
                "description": "Expired"
            },
            "hearingResults": [
                {
                    "pleaFindingType": {
                        "code": "GUILTY",
                        "description": "Guilty"
                    },
                    "findingType": {
                        "code": "PROVED",
                        "description": "Charge Proved"
                    },
                    "charge": {
                        "offence": {
                            "code": "51:2D",
                            "description": "Detains any person against his will - detention against will of staff (not prison offr)",
                            "type": {
                                "code": "51",
                                "description": "Prison Rule 51"
                            }
                        },
                        "offenceId": "1525933/2",
                        "chargeSequence": 2
                    },
                    "offence": {
                        "code": "51:2D",
                        "description": "Detains any person against his will - detention against will of staff (not prison offr)",
                        "type": {
                            "code": "51",
                            "description": "Prison Rule 51"
                        }
                    },
                    "resultAwards": [
                        {
                            "sequence": 6,
                            "sanctionType": {
                                "code": "FORFEIT",
                                "description": "Forfeiture of Privileges"
                            },
                            "sanctionStatus": {
                                "code": "IMMEDIATE",
                                "description": "Immediate"
                            },
                            "effectiveDate": "2023-08-15",
                            "statusDate": "2023-08-15",
                            "sanctionDays": 3,
                            "createdByUsername": "Q124UT",
                            "createdDateTime": "2023-08-15T08:58:08.015285",
                            "consecutiveAward": {
                                "sequence": 2,
                                "sanctionType": {
                                    "code": "ADA",
                                    "description": "Additional Days Added"
                                },
                                "sanctionStatus": {
                                    "code": "IMMEDIATE",
                                    "description": "Immediate"
                                },
                                "createdByUsername": "Q124UT",
                                "createdDateTime": "2023-08-15T08:58:08.015285",
                                "effectiveDate": "2023-08-07",
                                "statusDate": "2023-08-07",
                                "sanctionDays": 2,
                                "sanctionMonths": 1,
                                "chargeSequence": 1,
                                "adjudicationNumber": ${adjudicationNumber - 1} 
                            },
                            "chargeSequence": 2,
                            "adjudicationNumber": $adjudicationNumber
                        },
                        {
                            "sequence": 7,
                            "sanctionType": {
                                "code": "STOP_PCT",
                                "description": "Stoppage of Earnings (%)"
                            },
                            "sanctionStatus": {
                                "code": "IMMEDIATE",
                                "description": "Immediate"
                            },
                            "createdByUsername": "Q124UT",
                            "createdDateTime": "2023-08-15T08:58:08.015285",
                            "effectiveDate": "2023-08-15",
                            "statusDate": "2023-08-15",
                            "sanctionMonths": 2,
                            "compensationAmount": 120.12,
                            "chargeSequence": 2
                        }
                    ],
                    "createdDateTime": "2023-08-15T08:58:08.015285",
                    "createdByUsername": "AMARKE_GEN"
                }
            ],
            "createdDateTime": "2023-08-07T16:56:17.018049",
            "createdByUsername": "AMARKE_GEN",
            "notifications": []
        }
    ]
}    
  """.trimIndent()
}

private fun incidentResponse(
  id: Long = 1234,
): String =
  """
  {
    "incidentId": $id,
    "questionnaireId": 45456,
    "title": "This is a test incident",
    "description": "On 12/04/2023 approx 16:45 Mr Smith tried to escape.",
    "status":{
      "code": "AWAN",
      "description": "Awaiting Analysis",
      "listSequence": 1,
      "standardUser": true,
      "enhancedUser": true
    },
    "prison": {
      "code": "BXI",
      "description": "Brixton"
    },
    "type": "ATT_ESC_E",
    "lockedResponse": false,
    "incidentDateTime": "2017-04-12T16:45:00",
    "reportingStaff": {
      "username": "FSTAFF_GEN",
      "staffId": 485572,
      "firstName": "FRED",
      "lastName": "STAFF"
    },
    "reportedDateTime": "2024-02-06T12:36:00",
    "staffParties": [],
    "offenderParties": [],
    "requirements": [],
    "questions": [],
    "history": []
  }
  """.trimIndent()

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
    "createUsername": "ITAG1"
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
      "sunday": true
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
      "sunday": false
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
            "sunday": true
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
            "sunday": false
        }
    ]
}
  """.trimIndent()

private fun suspendedAllocationsResponse(): String =
  """
    [
      {
        "offenderNo": "A1234AA",
        "courseActivityId": 12345,
        "courseActivityDescription": "Kitchens AM"
      },
      {
        "offenderNo": "B1234BB",
        "courseActivityId": 12345,
        "courseActivityDescription": "Kitchens AM"
      },
      {
        "offenderNo": "A1234AA",
        "courseActivityId": 12346,
        "courseActivityDescription": "Kitchens PM"
      }
    ]
  """
