package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.net.HttpURLConnection
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(NomisApiService::class)
internal class NomisApiServiceTest {

  @Autowired
  private lateinit var nomisService: NomisApiService

  @Nested
  @DisplayName("getVisits")
  inner class GetVisits {
    @BeforeEach
    internal fun setUp() {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/visits/ids")
        ).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_OK)
            .withBody(visitPagedResponse())
        )
      )
    }

    @Test
    internal fun `will supply authentication token`() {
      nomisService.getVisits(
        prisonIds = listOf("MDI", "BXI"),
        visitTypes = listOf("SCON", "OFFI"),
        fromDateTime = LocalDateTime.parse("2020-01-01T01:30:00"),
        toDateTime = LocalDateTime.parse("2020-01-02T23:30:00"),
        ignoreMissingRoom = false,
        pageNumber = 23,
        pageSize = 10
      )
      nomisApi.verify(
        getRequestedFor(
          urlPathEqualTo("/visits/ids")
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
      )
    }

    @Test
    internal fun `will pass all filters when present`() {
      nomisService.getVisits(
        prisonIds = listOf("MDI", "BXI"),
        visitTypes = listOf("SCON", "OFFI"),
        fromDateTime = LocalDateTime.parse("2020-01-01T01:30:00"),
        toDateTime = LocalDateTime.parse("2020-01-02T23:30:00"),
        ignoreMissingRoom = true,
        pageNumber = 23,
        pageSize = 10
      )
      nomisApi.verify(
        getRequestedFor(
          urlEqualTo("/visits/ids?prisonIds=MDI&prisonIds=BXI&visitTypes=SCON&visitTypes=OFFI&fromDateTime=2020-01-01T01:30&toDateTime=2020-01-02T23:30&ignoreMissingRoom=true&page=23&size=10")
        )
      )
    }

    @Test
    internal fun `will pass empty filters when not present`() {
      nomisService.getVisits(
        prisonIds = listOf(),
        visitTypes = listOf(),
        fromDateTime = null,
        toDateTime = null,
        ignoreMissingRoom = false,
        pageNumber = 23,
        pageSize = 10
      )
      nomisApi.verify(
        getRequestedFor(
          urlEqualTo("/visits/ids?prisonIds&visitTypes&fromDateTime&toDateTime&ignoreMissingRoom=false&page=23&size=10")
        )
      )
    }

    @Test
    internal fun `will return paging info along with the visit ids`() {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/visits/ids")
        ).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_OK)
            .withBody(
              """
{
    "content": [
        {
            "visitId": 1
        },
        {
            "visitId": 2
        },
        {
            "visitId": 3
        },
        {
            "visitId": 4
        },
        {
            "visitId": 5
        },
        {
            "visitId": 6
        },
        {
            "visitId": 7
        },
        {
            "visitId": 8
        },
        {
            "visitId": 9
        },
        {
            "visitId": 10
        }
    ],
    "pageable": {
        "sort": {
            "empty": false,
            "sorted": true,
            "unsorted": false
        },
        "offset": 0,
        "pageSize": 10,
        "pageNumber": 23,
        "paged": true,
        "unpaged": false
    },
    "last": false,
    "totalPages": 4190,
    "totalElements": 41900,
    "size": 10,
    "number": 23,
    "sort": {
        "empty": false,
        "sorted": true,
        "unsorted": false
    },
    "first": true,
    "numberOfElements": 10,
    "empty": false
}                
      
    """
            )
        )
      )

      val visits = nomisService.getVisits(
        prisonIds = listOf("MDI", "BXI"),
        visitTypes = listOf("SCON", "OFFI"),
        fromDateTime = LocalDateTime.parse("2020-01-01T01:30:00"),
        ignoreMissingRoom = false,
        toDateTime = LocalDateTime.parse("2020-01-02T23:30:00"),
        pageNumber = 23,
        pageSize = 10
      )

      assertThat(visits.content).hasSize(10)
      assertThat(visits.content).extracting<Long>(VisitId::visitId).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
      assertThat(visits.totalPages).isEqualTo(4190)
      assertThat(visits.pageable.pageNumber).isEqualTo(23)
      assertThat(visits.totalElements).isEqualTo(41900)
    }

    private fun visitPagedResponse() = """
{
    "content": [
        {
            "visitId": 1
        },
        {
            "visitId": 2
        },
        {
            "visitId": 3
        },
        {
            "visitId": 4
        },
        {
            "visitId": 5
        },
        {
            "visitId": 6
        },
        {
            "visitId": 7
        },
        {
            "visitId": 8
        },
        {
            "visitId": 9
        },
        {
            "visitId": 10
        }
    ],
    "pageable": {
        "sort": {
            "empty": false,
            "sorted": true,
            "unsorted": false
        },
        "offset": 0,
        "pageSize": 10,
        "pageNumber": 23,
        "paged": true,
        "unpaged": false
    },
    "last": false,
    "totalPages": 4190,
    "totalElements": 41900,
    "size": 10,
    "number": 23,
    "sort": {
        "empty": false,
        "sorted": true,
        "unsorted": false
    },
    "first": true,
    "numberOfElements": 10,
    "empty": false
}                
      
    """.trimIndent()
  }

  @Nested
  @DisplayName("getVisit")
  inner class GetVisit {
    @BeforeEach
    internal fun setUp() {
      nomisApi.stubFor(
        get(
          urlPathMatching("/visits/[0-9]+")
        ).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_OK)
            .withBody(
              """
              {
              "visitId": 10309617,
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
              """.trimIndent()
            )
        )
      )
    }

    @Test
    internal fun `will supply authentication token`() {
      nomisService.getVisit(
        nomisVisitId = 10309617,
      )
      nomisApi.verify(
        getRequestedFor(
          urlPathEqualTo("/visits/10309617")
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE"))
      )
    }

    @Test
    internal fun `will return visit data`() {
      val visit = nomisService.getVisit(
        nomisVisitId = 10309617,
      )

      assertThat(visit.visitId).isEqualTo(10309617)
      assertThat(visit.visitors).extracting<Long>(NomisVisitor::personId).containsExactly(4729570, 4729580)
      assertThat(visit.commentText).isEqualTo("Not sure if this is the right place to be")
      assertThat(visit.startDateTime).isEqualTo(LocalDateTime.parse("2021-10-25T09:00:00"))
      assertThat(visit.endDateTime).isEqualTo(LocalDateTime.parse("2021-10-25T11:45:00"))
      assertThat(visit.offenderNo).isEqualTo("A7948DY")
      assertThat(visit.agencyInternalLocation?.code).isEqualTo("OFF_VIS")
      assertThat(visit.visitStatus.code).isEqualTo("SCH")
      assertThat(visit.visitType.code).isEqualTo("SCON")
    }

    @Nested
    @DisplayName("when visit does not exist in NOMIS")
    inner class WhenNotFound {
      @BeforeEach
      internal fun setUp() {
        nomisApi.stubFor(
          get(
            urlPathMatching("/visits/[0-9]+")
          ).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpURLConnection.HTTP_NOT_FOUND)
          )
        )
      }

      @Test
      internal fun `will throw an exception`() {
        assertThatThrownBy {
          nomisService.getVisit(
            nomisVisitId = 10309617,
          )
        }.isInstanceOf(WebClientResponseException.NotFound::class.java)
      }
    }
  }
}
