package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException.InternalServerError
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.ActivityMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.ActivityMigrateRequest.PayPerSession
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.AllocationMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.NomisPayRate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.NomisScheduleRule
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ActivitiesApiExtension.Companion.activitiesApi
import java.time.LocalDate

@SpringAPIServiceTest
@Import(ActivitiesApiService::class, ActivitiesConfiguration::class)
internal class ActivitiesApiServiceTest {
  @Autowired
  private lateinit var activitiesApiService: ActivitiesApiService

  @Nested
  inner class MigrateActivity {
    @BeforeEach
    internal fun setUp() {
      stubMigrateActivity()
    }

    private fun stubMigrateActivity(secondActivityId: Long? = 5555) {
      activitiesApi.stubFor(
        post(urlEqualTo("/migrate/activity")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(
              """
              {
                "prisonCode": "BXI",
                "activityId": 4444,
                "splitRegimeActivityId": $secondActivityId
              }
              """.trimIndent(),
            ),
        ),
      )
    }

    private fun migrateActivityRequest() =
      ActivityMigrateRequest(
        programServiceCode = "SOME_PROGRAM",
        prisonCode = "NXI",
        startDate = LocalDate.now().minusDays(1),
        endDate = LocalDate.now().plusDays(1),
        capacity = 10,
        description = "Some activity",
        payPerSession = PayPerSession.H,
        minimumIncentiveLevel = "BAS",
        runsOnBankHoliday = true,
        internalLocationDescription = "BXI-A-1-01",
        internalLocationCode = "CELL-01",
        internalLocationId = 123,
        scheduleRules = listOf(
          NomisScheduleRule(
            startTime = LocalDate.now().atTime(8, 0).toString(),
            endTime = LocalDate.now().atTime(11, 30).toString(),
            monday = true,
            tuesday = true,
            wednesday = true,
            thursday = false,
            friday = false,
            saturday = false,
            sunday = false,
          ),
          NomisScheduleRule(
            startTime = LocalDate.now().atTime(13, 0).toString(),
            endTime = LocalDate.now().atTime(15, 30).toString(),
            monday = false,
            tuesday = false,
            wednesday = true,
            thursday = true,
            friday = true,
            saturday = false,
            sunday = false,
          ),
        ),
        payRates = listOf(
          NomisPayRate(
            incentiveLevel = "BAS",
            rate = 120,
            nomisPayBand = "1",
          ),
          NomisPayRate(
            incentiveLevel = "BAS",
            rate = 140,
            nomisPayBand = "2",
          ),
        ),
      )

    @Test
    internal fun `should supply authentication token`(): Unit = runBlocking {
      activitiesApiService.migrateActivity(migrateActivityRequest())

      activitiesApi.verify(
        postRequestedFor(urlEqualTo("/migrate/activity"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data as JSON to endpoint`(): Unit = runBlocking {
      activitiesApiService.migrateActivity(migrateActivityRequest())

      activitiesApi.verify(
        postRequestedFor(urlEqualTo("/migrate/activity"))
          .withRequestBody(
            equalToJson(
              """
            {
              "programServiceCode": "SOME_PROGRAM",
              "prisonCode": "NXI",
              "startDate": "${LocalDate.now().minusDays(1)}",
              "endDate": "${LocalDate.now().plusDays(1)}",
              "capacity": 10,
              "description": "Some activity",
              "payPerSession": "H",
              "minimumIncentiveLevel": "BAS",
              "runsOnBankHoliday": true,
              "internalLocationDescription": "BXI-A-1-01",
              "internalLocationCode": "CELL-01",
              "internalLocationId": 123,
              "scheduleRules": [
                {
                  "startTime": "${LocalDate.now().atTime(8, 0)}",
                  "endTime": "${LocalDate.now().atTime(11, 30)}",
                  "monday": true,
                  "tuesday": true,
                  "wednesday": true,
                  "thursday": false,
                  "friday": false,
                  "saturday": false,
                  "sunday": false
                },
                {
                  "startTime": "${LocalDate.now().atTime(13, 0)}",
                  "endTime": "${LocalDate.now().atTime(15, 30)}",
                  "monday": false,
                  "tuesday": false,
                  "wednesday": true,
                  "thursday": true,
                  "friday": true,
                  "saturday": false,
                  "sunday": false
                }
              ],
              "payRates":[
                {
                  "incentiveLevel": "BAS",
                  "rate": 120,
                  "nomisPayBand": "1"
                },
                {
                  "incentiveLevel": "BAS",
                  "rate": 140,
                  "nomisPayBand": "2"
                }
              ]
            }
              """.trimIndent(),
            ),
          ),
      )
    }

    @Test
    internal fun `should return newly created Activities IDs`(): Unit = runBlocking {
      val response = activitiesApiService.migrateActivity(migrateActivityRequest())

      with(response) {
        assertThat(prisonCode).isEqualTo("BXI")
        assertThat(activityId).isEqualTo(4444)
        assertThat(splitRegimeActivityId).isEqualTo(5555)
      }
    }

    @Test
    internal fun `should handle null second activity id`(): Unit = runBlocking {
      stubMigrateActivity(secondActivityId = null)

      val response = activitiesApiService.migrateActivity(migrateActivityRequest())

      with(response) {
        assertThat(prisonCode).isEqualTo("BXI")
        assertThat(activityId).isEqualTo(4444)
        assertThat(splitRegimeActivityId).isNull()
      }
    }

    @Test
    internal fun `should throw exception for any error`() {
      activitiesApi.stubFor(
        post(urlPathMatching("/migrate/activity")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          activitiesApiService.migrateActivity(migrateActivityRequest())
        }
      }.isInstanceOf(InternalServerError::class.java)
    }
  }

  @Nested
  inner class MigrateAllocation {
    @BeforeEach
    internal fun setUp() {
      stubMigrateAllocation()
    }

    private fun stubMigrateAllocation() {
      activitiesApi.stubFor(
        post(urlEqualTo("/migrate/allocation")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(
              """
              {
                "activityId": 4444,
                "allocationId": 6666
              }
              """.trimIndent(),
            ),
        ),
      )
    }

    private fun migrateAllocationRequest() =
      AllocationMigrateRequest(
        prisonCode = "NXI",
        startDate = LocalDate.now().minusDays(1),
        endDate = LocalDate.now().plusDays(1),
        activityId = 4444,
        splitRegimeActivityId = 5555,
        prisonerNumber = "A1234AA",
        bookingId = 8888,
        suspendedFlag = true,
        cellLocation = "BXI-A-1-01",
        nomisPayBand = "1",
        endComment = "Ended",
      )

    @Test
    internal fun `should supply authentication token`(): Unit = runBlocking {
      activitiesApiService.migrateAllocation(migrateAllocationRequest())

      activitiesApi.verify(
        postRequestedFor(urlEqualTo("/migrate/allocation"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass data as JSON to endpoint`(): Unit = runBlocking {
      activitiesApiService.migrateAllocation(migrateAllocationRequest())

      activitiesApi.verify(
        postRequestedFor(urlEqualTo("/migrate/allocation"))
          .withRequestBody(
            equalToJson(
              """
                {
                  "prisonCode": "NXI",
                  "startDate": "${LocalDate.now().minusDays(1)}",
                  "endDate": "${LocalDate.now().plusDays(1)}",
                  "activityId": 4444,
                  "splitRegimeActivityId": 5555,
                  "prisonerNumber": "A1234AA",
                  "bookingId": 8888,
                  "suspendedFlag": true,
                  "cellLocation": "BXI-A-1-01",
                  "nomisPayBand": "1",
                  "endComment": "Ended"
                }
              """.trimIndent(),
            ),
          ),
      )
    }

    @Test
    internal fun `should return Activity and Allocation IDs`(): Unit = runBlocking {
      val response = activitiesApiService.migrateAllocation(migrateAllocationRequest())

      with(response) {
        assertThat(activityId).isEqualTo(4444)
        assertThat(allocationId).isEqualTo(6666)
      }
    }

    @Test
    internal fun `should throw exception for any error`() {
      activitiesApi.stubFor(
        post(urlPathMatching("/migrate/allocation")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThatThrownBy {
        runBlocking {
          activitiesApiService.migrateAllocation(migrateAllocationRequest())
        }
      }.isInstanceOf(InternalServerError::class.java)
    }
  }
}
