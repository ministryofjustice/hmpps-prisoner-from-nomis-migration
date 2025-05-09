package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.ActivityMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.ActivityMigrateRequest.PayPerSession
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.AllocationMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.NomisPayRate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.NomisScheduleRule
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.Slot.TimeSlot.AM
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ActivitiesApiExtension.Companion.activitiesApi
import java.time.LocalDate
import java.util.*

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

    fun migrateActivityRequest() = ActivityMigrateRequest(
      programServiceCode = "SOME_PROGRAM",
      prisonCode = "NXI",
      startDate = LocalDate.now().minusDays(1),
      endDate = LocalDate.now().plusDays(1),
      capacity = 10,
      description = "Some activity",
      payPerSession = PayPerSession.H,
      runsOnBankHoliday = true,
      dpsLocationId = UUID.fromString("c4ff80c3-3148-4e2f-a4c2-4f278ad8349a"),
      outsideWork = true,
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
          timeSlot = NomisScheduleRule.TimeSlot.AM,
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
          timeSlot = NomisScheduleRule.TimeSlot.PM,
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
    internal fun `should supply authentication token`() = runTest {
      activitiesApiService.migrateActivity(migrateActivityRequest())

      activitiesApi.verify(
        postRequestedFor(urlEqualTo("/migrate/activity"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data as JSON to endpoint`() = runTest {
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
              "runsOnBankHoliday": true,
              "dpsLocationId": "c4ff80c3-3148-4e2f-a4c2-4f278ad8349a",
              "outsideWork": true,
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
                  "sunday": false,
                  "timeSlot": "AM"
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
                  "sunday": false,
                  "timeSlot": "PM"
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
    internal fun `should return newly created Activities IDs`() = runTest {
      val response = activitiesApiService.migrateActivity(migrateActivityRequest())

      with(response) {
        assertThat(prisonCode).isEqualTo("BXI")
        assertThat(activityId).isEqualTo(4444)
        assertThat(splitRegimeActivityId).isEqualTo(5555)
      }
    }

    @Test
    internal fun `should handle null second activity id`() = runTest {
      stubMigrateActivity(secondActivityId = null)

      val response = activitiesApiService.migrateActivity(migrateActivityRequest())

      with(response) {
        assertThat(prisonCode).isEqualTo("BXI")
        assertThat(activityId).isEqualTo(4444)
        assertThat(splitRegimeActivityId).isNull()
      }
    }

    @Test
    internal fun `should throw exception for any error`() = runTest {
      activitiesApi.stubFor(
        post(urlPathMatching("/migrate/activity")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThrows<WebClientResponseException.InternalServerError> {
        activitiesApiService.migrateActivity(migrateActivityRequest())
      }
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

    private fun migrateAllocationRequest() = AllocationMigrateRequest(
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
      exclusions = listOf(
        Slot(
          weekNumber = 1,
          timeSlot = AM,
          monday = true,
          tuesday = false,
          wednesday = false,
          thursday = false,
          friday = false,
          saturday = false,
          sunday = false,
          daysOfWeek = setOf(Slot.DaysOfWeek.MONDAY),
        ),
      ),
    )

    @Test
    internal fun `should supply authentication token`() = runTest {
      activitiesApiService.migrateAllocation(migrateAllocationRequest())

      activitiesApi.verify(
        postRequestedFor(urlEqualTo("/migrate/allocation"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass data as JSON to endpoint`() = runTest {
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
                  "endComment": "Ended",
                  "exclusions": [
                  {
                    "weekNumber": 1,
                    "timeSlot": "AM",
                    "monday": true,
                    "tuesday": false,
                    "wednesday": false,
                    "thursday": false,
                    "friday": false,
                    "saturday": false,
                    "sunday": false,
                    "daysOfWeek": ["MONDAY"],
                    "customStartTime": null,
                    "customEndTime": null
                  }
  ]
                }
              """.trimIndent(),
            ),
          ),
      )
    }

    @Test
    internal fun `should return Activity and Allocation IDs`() = runTest {
      val response = activitiesApiService.migrateAllocation(migrateAllocationRequest())

      with(response) {
        assertThat(activityId).isEqualTo(4444)
        assertThat(allocationId).isEqualTo(6666)
      }
    }

    @Test
    internal fun `should throw exception for any error`() = runTest {
      activitiesApi.stubFor(
        post(urlPathMatching("/migrate/allocation")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .withBody("""{"message":"Tea"}"""),
        ),
      )

      assertThrows<WebClientResponseException.InternalServerError> {
        activitiesApiService.migrateAllocation(migrateAllocationRequest())
      }
    }
  }

  @Nested
  inner class MoveActivityStartDates {
    private val prisonCode = "BXI"
    private val newStartDate = LocalDate.now().plusDays(2)

    @BeforeEach
    internal fun setUp() {
      activitiesApi.stubMoveActivityStartDates()
    }

    @Test
    internal fun `should supply authentication token`() = runTest {
      activitiesApiService.moveActivityStartDates(prisonCode, newStartDate)

      activitiesApi.verify(
        postRequestedFor(urlPathEqualTo("/migrate/$prisonCode/move-activity-start-dates"))
          .withQueryParam("newActivityStartDate", equalTo("$newStartDate"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should return any errors`() = runTest {
      val response = activitiesApiService.moveActivityStartDates(prisonCode, newStartDate)

      assertThat(response).containsExactly("Error1", "Error2")
    }

    @Test
    internal fun `should throw exception for any error`() = runTest {
      activitiesApi.stubFor(
        post(urlPathEqualTo("/migrate/$prisonCode/move-activity-start-dates"))
          .withQueryParam("newActivityStartDate", equalTo("$newStartDate"))
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.BAD_REQUEST.value())
              .withBody("""{"userMessage":"Some error"}"""),
          ),
      )

      assertThrows<WebClientResponseException.BadRequest> {
        activitiesApiService.moveActivityStartDates(prisonCode, newStartDate)
      }
    }
  }
}
