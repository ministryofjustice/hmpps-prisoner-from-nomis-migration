package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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

fun appointmentIdsPagedResponse(
  totalElements: Long = 10,
  ids: List<Long> = (0L..10L).toList(),
  pageSize: Long = 10,
  pageNumber: Long = 0,
): String {
  val content = ids.map { """{ "eventId": $it }""" }.joinToString { it }
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
    }
  }
   
  """.trimIndent()
}

private fun keyDateAdjustmentResponse(
  bookingId: Long = 2,
  keyDateAdjustmentId: Long = 3,
  offenderNo: String = "G4803UT",
): String {
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
    }
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
  "adjudicationNumber":$adjudicationNumber,
  "offenderNo": "$offenderNo",
  "adjudicationSequence": 3,
  "bookingId": 1207292,
  "partyAddedDate": "2023-06-08",
  "incident": {
      "adjudicationIncidentId": 1503064,
      "reportingStaff": {
          "staffId": 485585,
          "firstName": "LUCY",
          "lastName": "BENNETT"
      },
      "incidentDate": "2023-06-08",
      "incidentTime": "12:00:00",
      "reportedDate": "2023-06-08",
      "reportedTime": "14:17:20",
      "internalLocation": {
          "locationId": 26149,
          "code": "GYM",
          "description": "MDI-PROG_ACT-GYM"
      },
      "incidentType": {
          "code": "GOV",
          "description": "Governor's Report"
      },
      "details": "Vera incited Brian Duckworth to set fire to a lamp\r\ndamages - the lamp\r\nevidence includes something in a bag with a reference number of 1234\r\nwitnessed by amarktest",
      "prison": {
          "code": "MDI",
          "description": "Moorland (HMP & YOI)"
      },
      "prisonerWitnesses": [],
      "prisonerVictims": [],
      "otherPrisonersInvolved": [],
      "reportingOfficers": [],
      "staffWitnesses": [],
      "staffVictims": [],
      "otherStaffInvolved": [],
      "repairs": []
    },
    "charge": {
        "offence": {
            "code": "51:12A",
            "description": "Has in his possession - (a) any unauthorised article, or (b) a greater quantity of any article than he is authorised to have - possession of unauthorised items",
            "type": {
                "code": "51",
                "description": "Prison Rule 51"
            }
        },
        "offenceId": "$adjudicationNumber/1",
        "chargeSequence": $chargeSequence
    },
    "investigations": [],
    "hearings": []
}
   
  """.trimIndent()
}

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
