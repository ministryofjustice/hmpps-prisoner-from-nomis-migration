package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.havingExactly
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AllocationExclusion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.FindActiveActivityIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.FindActiveAllocationIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ProfileDetailsNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.booking
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.profileDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.profileDetailsResponse
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.time.LocalDate
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(NomisApiService::class, ProfileDetailsNomisApiMockServer::class)
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
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
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
    internal fun `will not pass empty filters when not present`() {
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
          urlEqualTo("/visits/ids?ignoreMissingRoom=false&page=23&size=10"),
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
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
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
          pageNumber = 0,
          pageSize = 3,
        )
      }
      nomisApi.verify(
        getRequestedFor(
          urlPathEqualTo("/activities/ids"),
        )
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass all filters when present`() {
      runBlocking {
        nomisService.getActivityIds(
          prisonId = "BXI",
          courseActivityId = 123,
          pageNumber = 0,
          pageSize = 3,
        )
      }
      nomisApi.verify(
        getRequestedFor(
          urlEqualTo("/activities/ids?prisonId=BXI&courseActivityId=123&page=0&size=3"),
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
          pageNumber = 0,
          pageSize = 3,
        )
      }

      assertThat(activities.content).hasSize(3)
      assertThat(activities.content).extracting<Long>(FindActiveActivityIdsResponse::courseActivityId)
        .containsExactly(1, 2, 3)
      assertThat(activities.totalPages).isEqualTo(2)
      assertThat(activities.pageable.pageNumber).isEqualTo(0)
      assertThat(activities.totalElements).isEqualTo(4)
    }

    private fun activitiesPagedResponse() = """
{
    "content": [
      {
        "courseActivityId": 1,
        "hasScheduleRules": false
      },
      {
        "courseActivityId": 2,
        "hasScheduleRules": true
      },
      {
        "courseActivityId": 3,
        "hasScheduleRules": false
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
                      "sunday": true,
                      "slotCategoryCode": "AM"
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
                      "sunday": true,
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
                      "rate": 3.6
                    }
                  ],
                  "outsideWork": false
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
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
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
          pageNumber = 0,
          pageSize = 3,
        )
      }
      nomisApi.verify(
        getRequestedFor(
          urlPathEqualTo("/allocations/ids"),
        )
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass all filters when present`() {
      runBlocking {
        nomisService.getAllocationIds(
          prisonId = "BXI",
          courseActivityId = 123,
          pageNumber = 0,
          pageSize = 3,
        )
      }
      nomisApi.verify(
        getRequestedFor(
          urlEqualTo("/allocations/ids?prisonId=BXI&courseActivityId=123&page=0&size=3"),
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
          pageNumber = 0,
          pageSize = 3,
        )
      }

      assertThat(allocations.content).hasSize(3)
      assertThat(allocations.content).extracting<Long>(FindActiveAllocationIdsResponse::allocationId)
        .containsExactly(1, 2, 3)
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
                  "livingUnitDescription": "RSI-A-1-001",
                  "exclusions": [
                    {
                      "day": "MON",
                      "slot": "AM"
                    }
                  ],
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
                      "sunday": true,
                      "slotCategoryCode": "AM"
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
                      "sunday": true,
                      "slotCategoryCode": "PM"
                    }
                  ],
                  "activityStartDate": "2023-03-12"
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
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should return allocation data`(): Unit = runBlocking {
      val allocation = nomisService.getAllocation(allocationId = 3333)

      with(allocation) {
        assertThat(nomisId).isEqualTo("A1234BC")
        assertThat(bookingId).isEqualTo(12345)
        assertThat(startDate).isEqualTo("2023-03-12")
        assertThat(endDate).isEqualTo("2023-05-26")
        assertThat(endComment).isEqualTo("Removed due to schedule clash")
        assertThat(endReasonCode).isEqualTo("WDRAWN")
        assertThat(suspended).isEqualTo(false)
        assertThat(payBand).isEqualTo("1")
        assertThat(livingUnitDescription).isEqualTo("RSI-A-1-001")
        assertThat(exclusions).containsExactly(AllocationExclusion(day = AllocationExclusion.Day.MON, slot = AllocationExclusion.Slot.AM))
        assertThat(scheduleRules.size).isEqualTo(2)
        assertThat(scheduleRules[0].startTime).isEqualTo("09:00")
        assertThat(scheduleRules[0].endTime).isEqualTo("11:30")
        assertThat(scheduleRules[0].monday).isTrue()
        assertThat(scheduleRules[1].startTime).isEqualTo("13:00")
        assertThat(scheduleRules[1].endTime).isEqualTo("16:30")
        assertThat(scheduleRules[1].monday).isTrue()
        assertThat(activityStartDate).isEqualTo("2023-03-12")
      }
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
  inner class GetPrisonerIds {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      nomisApi.stubGetPrisonerIds()

      nomisService.getPrisonerIds(
        pageNumber = 0,
        pageSize = 20,
      )

      nomisApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass page params to service`() = runTest {
      nomisApi.stubGetPrisonerIds()

      nomisService.getPrisonerIds(
        pageNumber = 5,
        pageSize = 100,
      )

      nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/ids/all"))
          .withQueryParam("page", equalTo("5"))
          .withQueryParam("size", equalTo("100")),
      )
    }

    @Test
    fun `will return a page of prisoners ids`() = runTest {
      nomisApi.stubGetPrisonerIds(totalElements = 10, firstOffenderNo = "A0001KT")

      val prisonerIds = nomisService.getPrisonerIds(
        pageNumber = 5,
        pageSize = 100,
      )

      assertThat(prisonerIds.content).hasSize(10)
      assertThat(prisonerIds.content[0].offenderNo).isEqualTo("A0001KT")
      assertThat(prisonerIds.content[1].offenderNo).isEqualTo("A0002KT")
    }
  }

  @Nested
  inner class ProfileDetails {

    @Autowired
    private lateinit var profileDetailsNomisApi: ProfileDetailsNomisApiMockServer

    @Nested
    inner class GetProfileDetails {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        profileDetailsNomisApi.stubGetProfileDetails(
          offenderNo = "A1234AA",
          profileTypes = listOf("MARITAL", "CHILD"),
          bookingId = 12345,
        )

        nomisService.getProfileDetails(offenderNo = "A1234AA", profileTypes = listOf("MARITAL", "CHILD"), bookingId = 12345)

        profileDetailsNomisApi.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass parameters to the service`() = runTest {
        profileDetailsNomisApi.stubGetProfileDetails(
          offenderNo = "A1234AA",
          profileTypes = listOf("MARITAL", "CHILD"),
          bookingId = 12345,
        )

        nomisService.getProfileDetails(offenderNo = "A1234AA", profileTypes = listOf("MARITAL", "CHILD"), bookingId = 12345)

        profileDetailsNomisApi.verify(
          getRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details"))
            .withQueryParam("profileTypes", havingExactly("MARITAL", "CHILD"))
            .withQueryParam("bookingId", equalTo("12345")),
        )
      }

      @Test
      fun `will return profile details`() = runTest {
        profileDetailsNomisApi.stubGetProfileDetails(
          offenderNo = "A1234AA",
          profileTypes = listOf("MARITAL", "CHILD"),
          bookingId = 12345,
          response = profileDetailsResponse(
            offenderNo = "A1234AA",
            bookings = listOf(
              booking(
                profileDetails = listOf(
                  profileDetails("MARITAL", "M"),
                  profileDetails("CHILD", "3"),
                ),
              ),
            ),
          ),
        )

        val profileDetailsResponse = nomisService.getProfileDetails(
          offenderNo = "A1234AA",
          profileTypes = listOf("MARITAL", "CHILD"),
          bookingId = 12345,
        )

        with(profileDetailsResponse) {
          assertThat(offenderNo).isEqualTo("A1234AA")
          assertThat(bookings)
            .extracting("bookingId", "startDateTime", "latestBooking")
            .containsExactly(tuple(1L, LocalDateTime.parse("2024-02-03T12:34:56"), true))
          assertThat(bookings[0].profileDetails)
            .extracting("type", "code", "createdBy", "modifiedBy", "auditModuleName")
            .containsExactly(
              tuple("MARITAL", "M", "A_USER", "ANOTHER_USER", "NOMIS"),
              tuple("CHILD", "3", "A_USER", "ANOTHER_USER", "NOMIS"),
            )
          assertThat(bookings[0].profileDetails[0].createDateTime.toLocalDate()).isEqualTo(LocalDate.now())
          assertThat(bookings[0].profileDetails[0].modifiedDateTime!!.toLocalDate()).isEqualTo(LocalDate.now())
        }
      }

      @Test
      fun `will throw error when bookings do not exist`() = runTest {
        profileDetailsNomisApi.stubGetProfileDetails(status = NOT_FOUND)

        assertThrows<WebClientResponseException.NotFound> {
          nomisService.getProfileDetails(offenderNo = "A1234AA")
        }
      }

      @Test
      fun `will throw error when API returns an error`() = runTest {
        profileDetailsNomisApi.stubGetProfileDetails(status = INTERNAL_SERVER_ERROR)

        assertThrows<WebClientResponseException.InternalServerError> {
          nomisService.getProfileDetails(offenderNo = "A1234AA")
        }
      }
    }
  }

  @Nested
  inner class CheckServiceAgencyForPrisoner {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      nomisApi.stubCheckServiceAgencyForPrisoner()

      nomisService.isServiceAgencyOnForPrisoner(
        serviceCode = "VISIT_ALLOCATION",
        prisonNumber = "A1234BC",
      )

      nomisApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will call the  service endpoint`() = runTest {
      nomisApi.stubCheckServiceAgencyForPrisoner()

      nomisService.isServiceAgencyOnForPrisoner(
        serviceCode = "VISIT_ALLOCATION",
        prisonNumber = "A1234BC",
      )

      nomisApi.verify(getRequestedFor(urlPathEqualTo("/agency-switches/VISIT_ALLOCATION/prisoner/A1234BC")))
    }

    @Test
    fun `will return true when service is on for prisoner's agency`() = runTest {
      nomisApi.stubCheckServiceAgencyForPrisoner()

      assertThat(
        nomisService.isServiceAgencyOnForPrisoner(
          serviceCode = "VISIT_ALLOCATION",
          prisonNumber = "A1234BC",
        ),
      ).isTrue
    }

    @Test
    fun `will return false if exception thrown when service not on for prisoner's agency`() = runTest {
      nomisApi.stubCheckServiceAgencyForPrisonerNotFound()
      assertThat(
        nomisService.isServiceAgencyOnForPrisoner(
          serviceCode = "VISIT_ALLOCATION",
          prisonNumber = "A1234BC",
        ),
      ).isFalse
    }
  }

  @Nested
  inner class IsAgencySwitchOnForAgency {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      nomisApi.stubCheckAgencySwitchForAgency()

      nomisService.isAgencySwitchOnForAgency(
        serviceCode = "VISIT_ALLOCATION",
        agencyId = "MDI",
      )

      nomisApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will call the service endpoint`() = runTest {
      nomisApi.stubCheckAgencySwitchForAgency()

      nomisService.isAgencySwitchOnForAgency(
        serviceCode = "VISIT_ALLOCATION",
        agencyId = "MDI",
      )

      nomisApi.verify(getRequestedFor(urlPathEqualTo("/agency-switches/VISIT_ALLOCATION/agency/MDI")))
    }

    @Test
    fun `will return true when service is on for agency`() = runTest {
      nomisApi.stubCheckAgencySwitchForAgency()

      assertThat(
        nomisService.isAgencySwitchOnForAgency(
          serviceCode = "VISIT_ALLOCATION",
          agencyId = "MDI",
        ),
      ).isTrue
    }

    @Test
    fun `will return false if exception thrown when service not on for agency`() = runTest {
      nomisApi.stubCheckAgencySwitchForAgencyNotFound()
      assertThat(
        nomisService.isAgencySwitchOnForAgency(
          serviceCode = "VISIT_ALLOCATION",
          agencyId = "MDI",
        ),
      ).isFalse
    }
  }
}
