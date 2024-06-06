package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.fasterxml.jackson.databind.ObjectMapper
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
class IncidentsNomisApiMockServer(private val objectMapper: ObjectMapper) {
  companion object {
    const val INCIDENTS_ID_URL = "/incidents/ids"
  }
  fun stubHealthPing(status: Int) {
    nomisApi.stubFor(
      get("/health/ping").willReturn(
        aResponse().withHeader("Content-Type", "application/json").withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

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
              .withBody(incidentResponse(nomisIncidentId = it.toLong())),
          ),
      )
    }
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
  fun verify(countMatchingStrategy: CountMatchingStrategy, requestPatternBuilder: RequestPatternBuilder) = nomisApi.verify(countMatchingStrategy, requestPatternBuilder)
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

private fun incidentResponse(
  nomisIncidentId: Long = 1234,
): String =
  """
  {
    "incidentId": $nomisIncidentId,
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
    "agency": {
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
    "followUpDate": "2017-04-12",
    "createDateTime": "2024-02-06T12:36:00",
    "createdBy": "JIM SMITH",
    "reportedDateTime": "2024-02-06T12:36:00",
    "staffParties": [],
    "offenderParties": [],
    "requirements": [],
    "questions": [],
    "history": []
  }
  """.trimIndent()
