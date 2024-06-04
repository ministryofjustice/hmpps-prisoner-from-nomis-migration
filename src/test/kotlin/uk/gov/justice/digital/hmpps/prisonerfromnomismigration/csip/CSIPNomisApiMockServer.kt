package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.lang.Long.min

@Component
class CSIPNomisApiMockServer {
  companion object {
    const val CSIP_ID_URL = "/csip/ids"
  }
  fun stubHealthPing(status: Int) {
    nomisApi.stubFor(
      get("/health/ping").willReturn(
        aResponse().withHeader("Content-Type", "application/json").withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubMultipleGetCSIPIdCounts(totalElements: Long, pageSize: Long) {
    // for each page create a response for each csip id starting from 1 up to `totalElements`

    val pages = (totalElements / pageSize) + 1
    (0..pages).forEach { page ->
      val startId = (page * pageSize) + 1
      val endId = min((page * pageSize) + pageSize, totalElements)
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/csip/ids"),
        )
          .withQueryParam("page", equalTo(page.toString()))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(
                csipIdsPagedResponse(
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

  fun stubMultipleGetCSIP(intProgression: IntProgression) {
    (intProgression).forEach {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/csip/$it"),
        )
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(csipResponse(it.toLong())),
          ),
      )
    }
  }

  fun stubGetCSIP(nomisCSIPId: Long = 1234) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/csip/$nomisCSIPId"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(csipResponse(nomisCSIPId)),
        ),
    )
  }

  fun stubGetCSIPNotFound(nomisCSIPId: Long = 1234) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/csip/$nomisCSIPId"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value()),
        ),
    )
  }
  fun stubGetInitialCount(urlPath: String, totalElements: Long, pagedResponse: (totalElements: Long) -> String) = nomisApi.stubGetInitialCount(urlPath, totalElements, pagedResponse)
  fun verifyGetIdsCount(url: String, fromDate: String, toDate: String, prisonId: String? = null) = nomisApi.verifyGetIdsCount(url, fromDate, toDate, prisonId)
  fun verify(countMatchingStrategy: CountMatchingStrategy, requestPatternBuilder: RequestPatternBuilder) = nomisApi.verify(countMatchingStrategy, requestPatternBuilder)
  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun csipIdsPagedResponse(
  totalElements: Long = 10,
  ids: List<Long> = (0L..10L).toList(),
  pageSize: Long = 10,
  pageNumber: Long = 0,
): String {
  val content = ids.map { """{ "id": $it }""" }.joinToString { it }
  return pageContent(content, pageSize, pageNumber, totalElements, ids.size)
}

private fun csipResponse(
  nomisCSIPId: Long = 1234,
): String =
  """
  {
    "id":"$nomisCSIPId",
    "offender":{
      "offenderNo":"A1247EA",
      "firstName":"TOMMY",
      "lastName":"THIRD"
    },
    "bookingId":1214478,
    "originalAgencyId":"LEI",
    "logNumber":" LEI 2",
    "incidentDateTime":"2024-04-01T10:00:00",
    "type":{
      "code":"ATO",
      "description":"Abuse/Threats Other"
    },
    "location":{
      "code":"EXY",
      "description":"Exercise Yard"
    },
    "areaOfWork":{
      "code":"ACT",
      "description":"Activities"
    },
    "reportedBy":"jim_adm",
    "reportedDate":"2024-04-04",
    "proActiveReferral":false,
    "staffAssaulted":true,
    "staffAssaultedName":"somebody was hurt",
    "reportDetails":{
      "involvement":{
        "code":"PER",
        "description":"Perpertrator"
      },
      "concern":"description of concern goes in here",
      "factors":[
        {
          "id":43,
          "type":{
            "code":"BUL",
            "description":"Bullying"
          },
          "comment":"referral - continued screen comment goes here",
          "createDateTime":"2024-04-01T10:00:00",
          "createdBy":"JSMITH" 
        }
      ],
      "knownReasons":"known reasons details go in here",
      "otherInformation":"other information goes in here",
      "saferCustodyTeamInformed":false,
      "referralComplete":true,
      "referralCompletedBy":"JIM_ADM",
      "referralCompletedDate":"2024-04-04"
    },
    "saferCustodyScreening":{
      "outcome":{
        "code":"CUR",
        "description":"Progress to CSIP"
      },
      "recordedBy":"FRED_ADM",
      "recordedDate":"2024-04-08",
      "reasonForDecision":"There is a reason for the decision - it goes here"
    },
    "investigation":{
      "interviews":[]
    },
    "decision":{
      "decisionOutcome":{
        "code":"CUR",
        "description":"Progress to CSIP"
      },
      "recordedBy":"FRED_ADM",
      "recordedDate":"2024-04-08",
      "actions":{
        "openCSIPAlert":false,
        "nonAssociationsUpdated":false,
        "observationBook":false,
        "unitOrCellMove":false,
        "csraOrRsraReview":false,
        "serviceReferral":false,
        "simReferral":false
      }
    },
    "caseManager":"Jim Smith",
    "planReason":"helper",
    "firstCaseReviewDate":"2024-04-15",
    "plans":[
      {
        "id":65,
        "identifiedNeed":"they need help",
        "intervention":"dd",
        "progression":"d",
        "referredBy":"karen",
        "createdDate":"2024-04-16",
        "targetDate":"2024-08-20",
        "closedDate":"2024-04-17",
        "createDateTime":"2024-04-01T10:00:00",
        "createdBy":"JSMITH"
      }
    ],
    "reviews":[
      {
        "id":65,
        "reviewSequence":1,
        "attendees":[],
        "remainOnCSIP":true,
        "csipUpdated":false,
        "caseNote":false,
        "closeCSIP":true,
        "peopleInformed":false,
        "closeDate":"2024-04-16",
        "createDateTime":"2024-04-01T10:00:00",
        "createdBy":"JSMITH"
      }
    ],
    "documents":[],
    "createDateTime":"2024-04-01T10:00:00",
    "createdBy":"JSMITH"
  }
  """.trimIndent()
