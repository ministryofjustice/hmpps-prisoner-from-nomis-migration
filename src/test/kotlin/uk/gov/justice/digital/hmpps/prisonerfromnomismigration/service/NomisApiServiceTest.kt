package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.FindActiveActivityIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.FindActiveAllocationIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NonAssociationIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.time.LocalDate
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
          urlPathEqualTo("/visits/ids"),
        ).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_OK)
            .withBody(visitPagedResponse()),
        ),
      )
    }

    @Test
    internal fun `will supply authentication token`() {
      runBlocking {
        nomisService.getVisits(
          prisonIds = listOf("MDI", "BXI"),
          visitTypes = listOf("SCON", "OFFI"),
          fromDateTime = LocalDateTime.parse("2020-01-01T01:30:00"),
          toDateTime = LocalDateTime.parse("2020-01-02T23:30:00"),
          ignoreMissingRoom = false,
          pageNumber = 23,
          pageSize = 10,
        )
      }
      nomisApi.verify(
        getRequestedFor(
          urlPathEqualTo("/visits/ids"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass all filters when present`() {
      runBlocking {
        nomisService.getVisits(
          prisonIds = listOf("MDI", "BXI"),
          visitTypes = listOf("SCON", "OFFI"),
          fromDateTime = LocalDateTime.parse("2020-01-01T01:30:00"),
          toDateTime = LocalDateTime.parse("2020-01-02T23:30:00"),
          ignoreMissingRoom = true,
          pageNumber = 23,
          pageSize = 10,
        )
      }
      nomisApi.verify(
        getRequestedFor(
          urlEqualTo("/visits/ids?prisonIds=MDI&prisonIds=BXI&visitTypes=SCON&visitTypes=OFFI&fromDateTime=2020-01-01T01:30&toDateTime=2020-01-02T23:30&ignoreMissingRoom=true&page=23&size=10"),
        ),
      )
    }

    @Test
    internal fun `will pass empty filters when not present`() {
      runBlocking {
        nomisService.getVisits(
          prisonIds = listOf(),
          visitTypes = listOf(),
          fromDateTime = null,
          toDateTime = null,
          ignoreMissingRoom = false,
          pageNumber = 23,
          pageSize = 10,
        )
      }
      nomisApi.verify(
        getRequestedFor(
          urlEqualTo("/visits/ids?prisonIds&visitTypes&fromDateTime&toDateTime&ignoreMissingRoom=false&page=23&size=10"),
        ),
      )
    }

    @Test
    internal fun `will return paging info along with the visit ids`() {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/visits/ids"),
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
      
    """,
            ),
        ),
      )

      val visits = runBlocking {
        nomisService.getVisits(
          prisonIds = listOf("MDI", "BXI"),
          visitTypes = listOf("SCON", "OFFI"),
          fromDateTime = LocalDateTime.parse("2020-01-01T01:30:00"),
          ignoreMissingRoom = false,
          toDateTime = LocalDateTime.parse("2020-01-02T23:30:00"),
          pageNumber = 23,
          pageSize = 10,
        )
      }

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
          urlPathMatching("/visits/[0-9]+"),
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
                "commentText": "Not sure if this is the right place to be",
                "createUserId": "aUser",
                "whenCreated": "2021-10-25T09:00:00"
              }
              """.trimIndent(),
            ),
        ),
      )
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runBlocking {
      nomisService.getVisit(
        nomisVisitId = 10309617,
      )
      nomisApi.verify(
        getRequestedFor(
          urlPathEqualTo("/visits/10309617"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will return visit data`(): Unit = runBlocking {
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
            urlPathMatching("/visits/[0-9]+"),
          ).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpURLConnection.HTTP_NOT_FOUND),
          ),
        )
      }

      @Test
      internal fun `will throw an exception`() {
        assertThatThrownBy {
          runBlocking {
            nomisService.getVisit(
              nomisVisitId = 10309617,
            )
          }
        }.isInstanceOf(NotFound::class.java)
      }
    }
  }

  @Nested
  inner class GetActivities {
    @BeforeEach
    internal fun setUp() {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/activities/ids"),
        ).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_OK)
            .withBody(activitiesPagedResponse()),
        ),
      )
    }

    @Test
    internal fun `will supply authentication token`() {
      runBlocking {
        nomisService.getActivityIds(
          prisonId = "BXI",
          excludeProgramCodes = listOf("PROGRAM1", "PROGRAM2"),
          pageNumber = 0,
          pageSize = 3,
        )
      }
      nomisApi.verify(
        getRequestedFor(
          urlPathEqualTo("/activities/ids"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass all filters when present`() {
      runBlocking {
        nomisService.getActivityIds(
          prisonId = "BXI",
          excludeProgramCodes = listOf("PROGRAM1", "PROGRAM2"),
          courseActivityId = 123,
          pageNumber = 0,
          pageSize = 3,
        )
      }
      nomisApi.verify(
        getRequestedFor(
          urlEqualTo("/activities/ids?prisonId=BXI&excludeProgramCode=PROGRAM1&excludeProgramCode=PROGRAM2&courseActivityId=123&page=0&size=3"),
        ),
      )
    }

    @Test
    internal fun `will return paging info along with the activity ids`() {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/activities/ids"),
        ).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_OK)
            .withBody(activitiesPagedResponse()),
        ),
      )

      val activities = runBlocking {
        nomisService.getActivityIds(
          prisonId = "BXI",
          excludeProgramCodes = listOf("PROGRAM1", "PROGRAM2"),
          pageNumber = 0,
          pageSize = 3,
        )
      }

      assertThat(activities.content).hasSize(3)
      assertThat(activities.content).extracting<Long>(FindActiveActivityIdsResponse::courseActivityId).containsExactly(1, 2, 3)
      assertThat(activities.totalPages).isEqualTo(2)
      assertThat(activities.pageable.pageNumber).isEqualTo(0)
      assertThat(activities.totalElements).isEqualTo(4)
    }

    private fun activitiesPagedResponse() = """
{
    "content": [
      {
        "courseActivityId": 1
      },
      {
        "courseActivityId": 2
      },
      {
        "courseActivityId": 3
      }
    ],
    "pageable": {
        "sort": {
            "empty": false,
            "sorted": true,
            "unsorted": false
        },
        "offset": 0,
        "pageSize": 3,
        "pageNumber": 1,
        "paged": true,
        "unpaged": false
    },
    "last": false,
    "totalPages": 2,
    "totalElements": 4,
    "size": 3,
    "number": 0,
    "sort": {
        "empty": false,
        "sorted": true,
        "unsorted": false
    },
    "first": true,
    "numberOfElements": 3,
    "empty": false
}                
      
    """.trimIndent()
  }

  @Nested
  inner class GetActivity {
    @BeforeEach
    internal fun setUp() {
      nomisApi.stubFor(
        get(
          urlPathMatching("/activities/[0-9]+"),
        ).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_OK)
            .withBody(
              """
                {
                  "courseActivityId": 3333,
                  "programCode": "INDUCTION",
                  "prisonId": "BXI",
                  "startDate": "${LocalDate.now().minusDays(1)}",
                  "endDate": "${LocalDate.now().plusDays(1)}",
                  "internalLocationId": 1234,
                  "internalLocationCode": "KITCH",
                  "internalLocationDescription": "BXI-WORK_IND-KITCH",
                  "capacity": 10,
                  "description": "Kitchen work",
                  "minimumIncentiveLevel": "BAS",
                  "excludeBankHolidays": true,
                  "payPerSession": "H",
                  "scheduleRules": [
                    {
                      "startTime": "09:00",
                      "endTime": "11:30",
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
                      "endTime": "16:30",
                      "monday": true,
                      "tuesday": true,
                      "wednesday": true,
                      "thursday": true,
                      "friday": true,
                      "saturday": true,
                      "sunday": true
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
                      "rate": 3.6
                    }
                  ]
                }
              """.trimIndent(),
            ),
        ),
      )
    }

    @Test
    internal fun `should supply authentication token`(): Unit = runBlocking {
      nomisService.getActivity(courseActivityId = 3333)

      nomisApi.verify(
        getRequestedFor(
          urlPathEqualTo("/activities/3333"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should return activities data`(): Unit = runBlocking {
      val activity = nomisService.getActivity(courseActivityId = 3333)

      assertThat(activity.courseActivityId).isEqualTo(3333)
      assertThat(activity.programCode).isEqualTo("INDUCTION")
      assertThat(activity.prisonId).isEqualTo("BXI")
      assertThat(activity.startDate).isEqualTo(LocalDate.now().minusDays(1))
      assertThat(activity.endDate).isEqualTo(LocalDate.now().plusDays(1))
      assertThat(activity.internalLocationId).isEqualTo(1234)
      assertThat(activity.internalLocationCode).isEqualTo("KITCH")
      assertThat(activity.internalLocationDescription).isEqualTo("BXI-WORK_IND-KITCH")
      assertThat(activity.capacity).isEqualTo(10)
      assertThat(activity.description).isEqualTo("Kitchen work")
      assertThat(activity.minimumIncentiveLevel).isEqualTo("BAS")
      assertThat(activity.excludeBankHolidays).isEqualTo(true)
      assertThat(activity.payPerSession).isEqualTo("H")
      assertThat(activity.scheduleRules[0].startTime).isEqualTo("09:00")
      assertThat(activity.scheduleRules[0].endTime).isEqualTo("11:30")
      assertThat(activity.scheduleRules[0].monday).isEqualTo(true)
      assertThat(activity.scheduleRules[0].tuesday).isEqualTo(true)
      assertThat(activity.scheduleRules[0].wednesday).isEqualTo(true)
      assertThat(activity.scheduleRules[0].thursday).isEqualTo(true)
      assertThat(activity.scheduleRules[0].friday).isEqualTo(true)
      assertThat(activity.scheduleRules[0].saturday).isEqualTo(true)
      assertThat(activity.scheduleRules[1].startTime).isEqualTo("13:00")
      assertThat(activity.scheduleRules[1].endTime).isEqualTo("16:30")
      assertThat(activity.scheduleRules[1].monday).isEqualTo(true)
      assertThat(activity.scheduleRules[1].tuesday).isEqualTo(true)
      assertThat(activity.scheduleRules[1].wednesday).isEqualTo(true)
      assertThat(activity.scheduleRules[1].thursday).isEqualTo(true)
      assertThat(activity.scheduleRules[1].friday).isEqualTo(true)
      assertThat(activity.scheduleRules[1].saturday).isEqualTo(true)
      assertThat(activity.scheduleRules[1].sunday).isEqualTo(true)
      assertThat(activity.payRates[0].incentiveLevelCode).isEqualTo("BAS")
      assertThat(activity.payRates[0].payBand).isEqualTo("1")
      assertThat(activity.payRates[0].rate).isEqualTo(BigDecimal.valueOf(3.2))
      assertThat(activity.payRates[1].incentiveLevelCode).isEqualTo("BAS")
      assertThat(activity.payRates[1].payBand).isEqualTo("2")
      assertThat(activity.payRates[1].rate).isEqualTo(BigDecimal.valueOf(3.6))
    }

    @Nested
    @DisplayName("should return not found")
    inner class WhenNotFound {
      @BeforeEach
      internal fun setUp() {
        nomisApi.stubFor(
          get(
            urlPathMatching("/activities/[0-9]+"),
          ).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpURLConnection.HTTP_NOT_FOUND),
          ),
        )
      }

      @Test
      internal fun `will throw an exception`() {
        assertThatThrownBy {
          runBlocking {
            nomisService.getActivity(courseActivityId = 3333)
          }
        }.isInstanceOf(NotFound::class.java)
      }
    }
  }

  @Nested
  inner class GetAllocations {
    @BeforeEach
    internal fun setUp() {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/allocations/ids"),
        ).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_OK)
            .withBody(allocationsPagedResponse()),
        ),
      )
    }

    @Test
    internal fun `will supply authentication token`() {
      runBlocking {
        nomisService.getAllocationIds(
          prisonId = "BXI",
          excludeProgramCodes = listOf("PROGRAM1", "PROGRAM2"),
          pageNumber = 0,
          pageSize = 3,
        )
      }
      nomisApi.verify(
        getRequestedFor(
          urlPathEqualTo("/allocations/ids"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass all filters when present`() {
      runBlocking {
        nomisService.getAllocationIds(
          prisonId = "BXI",
          excludeProgramCodes = listOf("PROGRAM1", "PROGRAM2"),
          courseActivityId = 123,
          pageNumber = 0,
          pageSize = 3,
        )
      }
      nomisApi.verify(
        getRequestedFor(
          urlEqualTo("/allocations/ids?prisonId=BXI&excludeProgramCode=PROGRAM1&excludeProgramCode=PROGRAM2&courseActivityId=123&page=0&size=3"),
        ),
      )
    }

    @Test
    internal fun `will return paging info along with the activity ids`() {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/allocations/ids"),
        ).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_OK)
            .withBody(allocationsPagedResponse()),
        ),
      )

      val allocations = runBlocking {
        nomisService.getAllocationIds(
          prisonId = "BXI",
          excludeProgramCodes = listOf("PROGRAM1", "PROGRAM2"),
          pageNumber = 0,
          pageSize = 3,
        )
      }

      assertThat(allocations.content).hasSize(3)
      assertThat(allocations.content).extracting<Long>(FindActiveAllocationIdsResponse::allocationId).containsExactly(1, 2, 3)
      assertThat(allocations.totalPages).isEqualTo(2)
      assertThat(allocations.pageable.pageNumber).isEqualTo(0)
      assertThat(allocations.totalElements).isEqualTo(4)
    }

    private fun allocationsPagedResponse() = """
{
    "content": [
      {
        "allocationId": 1
      },
      {
        "allocationId": 2
      },
      {
        "allocationId": 3
      }
    ],
    "pageable": {
        "sort": {
            "empty": false,
            "sorted": true,
            "unsorted": false
        },
        "offset": 0,
        "pageSize": 3,
        "pageNumber": 1,
        "paged": true,
        "unpaged": false
    },
    "last": false,
    "totalPages": 2,
    "totalElements": 4,
    "size": 3,
    "number": 0,
    "sort": {
        "empty": false,
        "sorted": true,
        "unsorted": false
    },
    "first": true,
    "numberOfElements": 3,
    "empty": false
}                
      
    """.trimIndent()
  }

  @Nested
  inner class GetAllocation {
    @BeforeEach
    internal fun setUp() {
      nomisApi.stubFor(
        get(
          urlPathMatching("/allocations/[0-9]+"),
        ).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_OK)
            .withBody(
              """
                {
                  "prisonId": "BXI",
                  "courseActivityId": 123,
                  "nomisId": "A1234BC",
                  "bookingId": 12345,
                  "startDate": "2023-03-12",
                  "endDate": "2023-05-26",
                  "endComment": "Removed due to schedule clash",
                  "endReasonCode": "WDRAWN",
                  "suspended": false,
                  "payBand": "1",
                  "livingUnitDescription": "RSI-A-1-001"
                }
              """.trimIndent(),
            ),
        ),
      )
    }

    @Test
    internal fun `should supply authentication token`(): Unit = runBlocking {
      nomisService.getAllocation(allocationId = 3333)

      nomisApi.verify(
        getRequestedFor(
          urlPathEqualTo("/allocations/3333"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should return allocation data`(): Unit = runBlocking {
      val allocation = nomisService.getAllocation(allocationId = 3333)

      assertThat(allocation.nomisId).isEqualTo("A1234BC")
      assertThat(allocation.bookingId).isEqualTo(12345)
      assertThat(allocation.startDate).isEqualTo("2023-03-12")
      assertThat(allocation.endDate).isEqualTo("2023-05-26")
      assertThat(allocation.endComment).isEqualTo("Removed due to schedule clash")
      assertThat(allocation.endReasonCode).isEqualTo("WDRAWN")
      assertThat(allocation.suspended).isEqualTo(false)
      assertThat(allocation.payBand).isEqualTo("1")
      assertThat(allocation.livingUnitDescription).isEqualTo("RSI-A-1-001")
    }

    @Nested
    @DisplayName("should return not found")
    inner class WhenNotFound {
      @BeforeEach
      internal fun setUp() {
        nomisApi.stubFor(
          get(
            urlPathMatching("/allocations/[0-9]+"),
          ).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpURLConnection.HTTP_NOT_FOUND),
          ),
        )
      }

      @Test
      internal fun `will throw an exception`() {
        assertThatThrownBy {
          runBlocking {
            nomisService.getAllocation(allocationId = 3333)
          }
        }.isInstanceOf(NotFound::class.java)
      }
    }
  }

  @Nested
  @DisplayName("getNonAssociation")
  inner class GetNonAssociation {
    val nonAssociationUrl = "/non-associations/offender/[A-Z]\\d{4}[A-Z]{2}/ns-offender/[A-Z]\\d{4}[A-Z]{2}\\?typeSequence=\\d"

    @BeforeEach
    internal fun setUp() {
      nomisApi.stubFor(
        get(
          urlMatching(nonAssociationUrl),
        ).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_OK)
            .withBody(
              """                
                {
                  "offenderNo": "A1234BC",
                  "nsOffenderNo": "D5678EF",
                  "typeSequence": 1,
                  "reason": "VIC",
                  "recipReason": "PER",
                  "type": "WING",
                  "authorisedBy": "Jim Smith",
                  "effectiveDate": "2023-10-25",
                  "expiryDate": "2023-10-26",
                  "comment": "Fight on Wing C"
                }
              """.trimIndent(),
            ),
        ),
      )
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runBlocking {
      nomisService.getNonAssociation(
        offenderNo = "A1234BC",
        nsOffenderNo = "D5678EF",
        typeSequence = 1,
      )
      nomisApi.verify(
        getRequestedFor(
          urlEqualTo("/non-associations/offender/A1234BC/ns-offender/D5678EF?typeSequence=1"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will return non-association data`(): Unit = runBlocking {
      val nonAssociation = nomisService.getNonAssociation(
        offenderNo = "A1234BC",
        nsOffenderNo = "D5678EF",
        typeSequence = 1,
      )
      assertThat(nonAssociation.offenderNo).isEqualTo("A1234BC")
      assertThat(nonAssociation.nsOffenderNo).isEqualTo("D5678EF")
      assertThat(nonAssociation.typeSequence).isEqualTo(1)
      assertThat(nonAssociation.reason).isEqualTo("VIC")
      assertThat(nonAssociation.recipReason).isEqualTo("PER")
      assertThat(nonAssociation.type).isEqualTo("WING")
      assertThat(nonAssociation.authorisedBy).isEqualTo("Jim Smith")
      assertThat(nonAssociation.effectiveDate).isEqualTo(LocalDate.parse("2023-10-25"))
      assertThat(nonAssociation.expiryDate).isEqualTo(LocalDate.parse("2023-10-26"))
      assertThat(nonAssociation.comment).isEqualTo("Fight on Wing C")
    }

    @Nested
    @DisplayName("when non-association with minimal data exists in NOMIS")
    inner class WithMinimalData {
      @BeforeEach
      internal fun setUp() {
        nomisApi.stubFor(
          get(
            urlMatching(nonAssociationUrl),
          ).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpURLConnection.HTTP_OK)
              .withBody(
                """
                  {
                  "offenderNo": "A1234BC",
                  "nsOffenderNo": "D5678EF",
                  "typeSequence": 1,
                  "reason": "VIC",
                  "recipReason": "PER",
                  "type": "WING",
                  "effectiveDate": "2023-10-25"
                }
                """.trimIndent(),
              ),
          ),
        )
      }

      @Test
      internal fun `will return non-association data and successfully map nullable fields`(): Unit = runBlocking {
        val nonAssociation = nomisService.getNonAssociation(
          offenderNo = "A1234BC",
          nsOffenderNo = "D5678EF",
          typeSequence = 1,
        )
        assertThat(nonAssociation.offenderNo).isEqualTo("A1234BC")
        assertThat(nonAssociation.nsOffenderNo).isEqualTo("D5678EF")
        assertThat(nonAssociation.typeSequence).isEqualTo(1)
        assertThat(nonAssociation.reason).isEqualTo("VIC")
        assertThat(nonAssociation.recipReason).isEqualTo("PER")
        assertThat(nonAssociation.type).isEqualTo("WING")
        assertThat(nonAssociation.authorisedBy).isNull()
        assertThat(nonAssociation.effectiveDate).isEqualTo(LocalDate.parse("2023-10-25"))
        assertThat(nonAssociation.expiryDate).isNull()
        assertThat(nonAssociation.comment).isNull()
      }
    }

    @Nested
    @DisplayName("when non-association does not exist in NOMIS")
    inner class WhenNotFound {
      @BeforeEach
      internal fun setUp() {
        nomisApi.stubFor(
          get(
            urlMatching(nonAssociationUrl),
          ).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpURLConnection.HTTP_NOT_FOUND),
          ),
        )
      }

      @Test
      internal fun `will throw an exception`() {
        assertThatThrownBy {
          runBlocking {
            nomisService.getNonAssociation(
              offenderNo = "A1234BC",
              nsOffenderNo = "D5678EF",
              typeSequence = 1,
            )
          }
        }.isInstanceOf(NotFound::class.java)
      }
    }
  }

  @Nested
  @DisplayName("getNonAssociations")
  inner class GetAllNonAssociations {
    val nonAssociationsUrl = "/non-associations/offender/[A-Z]\\d{4}[A-Z]{2}/ns-offender/[A-Z]\\d{4}[A-Z]{2}\\/all"

    @BeforeEach
    internal fun setUp() {
      nomisApi.stubFor(
        get(
          urlPathMatching(nonAssociationsUrl),
        ).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_OK)
            .withBody(
              """
                [
                  {
                    "offenderNo": "A1234BC",
                    "nsOffenderNo": "D5678EF",
                    "typeSequence": 1,
                    "reason": "VIC",
                    "recipReason": "PER",
                    "type": "WING",
                    "authorisedBy": "Jim Smith",
                    "effectiveDate": "2023-10-25",
                    "expiryDate": "2023-10-26",
                    "comment": "Fight on Wing C"
                  },
                  {
                    "offenderNo": "A1234BC",
                    "nsOffenderNo": "D5678EF",
                    "typeSequence": 2,
                    "reason": "RIV",
                    "recipReason": "RIV",
                    "type": "LAND",
                    "effectiveDate": "2023-08-31",
                    "expiryDate": "2023-09-01",
                    "comment": "tester"
                  }
                ]
              """.trimIndent(),
            ),
        ),
      )
    }

    @Test
    internal fun `will supply authentication token`(): Unit = runBlocking {
      nomisService.getNonAssociations(
        offenderNo = "A1234BC",
        nsOffenderNo = "D5678EF",
      )
      nomisApi.verify(
        getRequestedFor(
          urlEqualTo("/non-associations/offender/A1234BC/ns-offender/D5678EF/all"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will return non-association data`(): Unit = runBlocking {
      val nonAssociations = nomisService.getNonAssociations(
        offenderNo = "A1234BC",
        nsOffenderNo = "D5678EF",
      )
      with(nonAssociations[0]) {
        assertThat(offenderNo).isEqualTo("A1234BC")
        assertThat(nsOffenderNo).isEqualTo("D5678EF")
        assertThat(typeSequence).isEqualTo(1)
        assertThat(reason).isEqualTo("VIC")
        assertThat(recipReason).isEqualTo("PER")
        assertThat(type).isEqualTo("WING")
        assertThat(authorisedBy).isEqualTo("Jim Smith")
        assertThat(effectiveDate).isEqualTo(LocalDate.parse("2023-10-25"))
        assertThat(expiryDate).isEqualTo(LocalDate.parse("2023-10-26"))
        assertThat(comment).isEqualTo("Fight on Wing C")
      }
      with(nonAssociations[1]) {
        assertThat(offenderNo).isEqualTo("A1234BC")
        assertThat(nsOffenderNo).isEqualTo("D5678EF")
        assertThat(typeSequence).isEqualTo(2)
        assertThat(reason).isEqualTo("RIV")
        assertThat(recipReason).isEqualTo("RIV")
        assertThat(type).isEqualTo("LAND")
        assertThat(authorisedBy).isNull()
        assertThat(effectiveDate).isEqualTo(LocalDate.parse("2023-08-31"))
        assertThat(expiryDate).isEqualTo(LocalDate.parse("2023-09-01"))
        assertThat(comment).isEqualTo("tester")
      }
    }

    @Nested
    @DisplayName("when non-associations don't exist in NOMIS")
    inner class WhenNotFound {
      @BeforeEach
      internal fun setUp() {
        nomisApi.stubFor(
          get(
            urlMatching(nonAssociationsUrl),
          ).willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpURLConnection.HTTP_NOT_FOUND),
          ),
        )
      }

      @Test
      internal fun `will throw an exception`() {
        assertThatThrownBy {
          runBlocking {
            nomisService.getNonAssociations(
              offenderNo = "A1234BC",
              nsOffenderNo = "D5678EF",
            )
          }
        }.isInstanceOf(NotFound::class.java)
      }
    }
  }

  @Nested
  inner class GetNonAssociationIds {
    @BeforeEach
    internal fun setUp() {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/non-associations/ids"),
        ).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_OK)
            .withBody(nonAssociationsPagedResponse()),
        ),
      )
    }

    @Test
    internal fun `will supply authentication token`() {
      runBlocking {
        nomisService.getNonAssociationIds(
          pageNumber = 0,
          pageSize = 3,
          fromDate = null,
          toDate = null,
        )
      }
      nomisApi.verify(
        getRequestedFor(
          urlPathEqualTo("/non-associations/ids"),
        )
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass all filters`() {
      runBlocking {
        nomisService.getNonAssociationIds(
          pageNumber = 0,
          pageSize = 3,
          fromDate = null,
          toDate = null,
        )
      }
      nomisApi.verify(
        getRequestedFor(
          urlEqualTo("/non-associations/ids?fromDate&toDate&page=0&size=3"),
        ),
      )
    }

    @Test
    internal fun `will pass all filters when present`() {
      runBlocking {
        nomisService.getNonAssociationIds(
          pageNumber = 0,
          pageSize = 3,
          fromDate = LocalDate.parse("2020-01-01"),
          toDate = LocalDate.parse("2020-01-02"),
        )
      }
      nomisApi.verify(
        getRequestedFor(
          urlEqualTo("/non-associations/ids?fromDate=2020-01-01&toDate=2020-01-02&page=0&size=3"),
        ),
      )
    }

    @Test
    internal fun `will return paging info along with the non-association ids`() {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/non-associations/ids"),
        ).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpURLConnection.HTTP_OK)
            .withBody(nonAssociationsPagedResponse()),
        ),
      )

      val nonAssociations = runBlocking {
        nomisService.getNonAssociationIds(
          pageNumber = 0,
          pageSize = 3,
          fromDate = null,
          toDate = null,
        )
      }

      assertThat(nonAssociations.content).hasSize(3)
      assertThat(nonAssociations.content).extracting<String>(NonAssociationIdResponse::offenderNo1)
        .containsExactly("A1234BC", "G1234HI", "M1234NO")
      assertThat(nonAssociations.content).extracting<String>(NonAssociationIdResponse::offenderNo2)
        .containsExactly("D5678EF", "J5678KL", "P5678QR")
      assertThat(nonAssociations.totalPages).isEqualTo(2)
      assertThat(nonAssociations.pageable.pageNumber).isEqualTo(0)
      assertThat(nonAssociations.totalElements).isEqualTo(4)
    }

    private fun nonAssociationsPagedResponse() = """
{
    "content": [
      {
        "offenderNo1": "A1234BC",
        "offenderNo2": "D5678EF"
      },
      {
        "offenderNo1": "G1234HI",
        "offenderNo2": "J5678KL"
      },
      {
        "offenderNo1": "M1234NO",
        "offenderNo2": "P5678QR"
      }
    ],
    "pageable": {
        "sort": {
            "empty": false,
            "sorted": true,
            "unsorted": false
        },
        "offset": 0,
        "pageSize": 3,
        "pageNumber": 1,
        "paged": true,
        "unpaged": false
    },
    "last": false,
    "totalPages": 2,
    "totalElements": 4,
    "size": 3,
    "number": 0,
    "sort": {
        "empty": false,
        "sorted": true,
        "unsorted": false
    },
    "first": true,
    "numberOfElements": 3,
    "empty": false
}                
      
    """.trimIndent()
  }
}
