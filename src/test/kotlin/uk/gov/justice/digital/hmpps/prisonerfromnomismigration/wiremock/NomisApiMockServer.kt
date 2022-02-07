package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
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
    @JvmField
    val nomisApi = NomisApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    nomisApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    nomisApi.resetRequests()
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
          .withStatus(status)
      )
    )
  }

  fun stubGetVisitsInitialCount(totalElements: Long) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/visits/ids")
      )
        .withQueryParam("page", equalTo("0"))
        .withQueryParam("size", equalTo("1"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(visitPagedResponse(totalElements = totalElements))
        )
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
          urlPathEqualTo("/visits/ids")
        )
          .withQueryParam("page", equalTo(page.toString()))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(
                visitPagedResponse(
                  totalElements = totalElements,
                  visitIds = (startVisitId..endVisitId).map { it },
                  pageNumber = page,
                  pageSize = pageSize
                ),
              )
          )
      )
    }
  }

  fun stubMultipleGetVisits(totalElements: Long) {
    (1..totalElements).forEach {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/visits/$it")
        )
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(visitResponse(it))
          )
      )
    }
  }
}

private fun visitResponse(visitId: Long) = """
              {
              "visitId": $visitId,
              "offenderNo": "A7948DY",
              "startDateTime": "2021-10-25T09:00:00",
              "endDateTime": "2021-10-25T11:45:00",
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

private fun visitPagedResponse(
  totalElements: Long = 10,
  visitIds: List<Long> = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
  pageSize: Long = 10,
  pageNumber: Long = 0,
): String {
  val content = visitIds.map { """{ "visitId": $it }""" }.joinToString { it }
  return """
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
    "numberOfElements": ${visitIds.size},
    "empty": false
}                
      
  """.trimIndent()
}
