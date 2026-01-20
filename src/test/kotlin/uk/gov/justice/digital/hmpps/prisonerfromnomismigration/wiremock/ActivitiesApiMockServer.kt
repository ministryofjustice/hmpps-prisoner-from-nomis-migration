package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.notMatching
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments.sampleAppointmentInstance
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ActivitiesApiExtension.Companion.activitiesApi
import java.time.LocalDate

class ActivitiesApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val activitiesApi = ActivitiesApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    activitiesApi.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    activitiesApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    activitiesApi.stop()
  }
}

class ActivitiesApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8086
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubCreateAppointmentForMigration(appointmentInstanceId: Long?) {
    val response = appointmentInstanceId?.let { jsonMapper.writeValueAsString(sampleAppointmentInstance(it)) }

    stubFor(
      post(urlMatching("/migrate-appointment"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .apply {
              response?.run { withBody(this) }
            },
        ),
    )
  }

  fun verifyCreatedDate(createdValue: String, updatedValue: String) = verify(
    postRequestedFor(urlPathEqualTo("/migrate-appointment"))
      .withRequestBody(matchingJsonPath("created", equalTo(createdValue)))
      .withRequestBody(matchingJsonPath("updated", equalTo(updatedValue))),
  )

  fun createAppointmentCount() = findAll(postRequestedFor(urlMatching("/migrate-appointment"))).count()

  fun stubCreateActivityForMigration(activityScheduleId: Long = 4444, activityScheduleId2: Long? = 5555) {
    stubFor(
      post(urlMatching("/migrate/activity"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(
              """
              {
                "prisonCode": "BXI",
                "activityId": $activityScheduleId,
                "splitRegimeActivityId": $activityScheduleId2
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  fun stubCreateAllocationForMigration(count: Int, allocationId: Long = 4444, activityScheduleId: Long = 5555) {
    repeat(count) { offset ->
      stubFor(
        post(urlMatching("/migrate/allocation"))
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.CREATED.value())
              .withBody(
                """
              {
                "activityId": 1234,
                "allocationId": ${allocationId + offset},
                "activityScheduleId": ${activityScheduleId + offset }
              }
                """.trimIndent(),
              ),
          ),
      )
    }
  }

  fun verifyCreateAllocationsForMigration(count: Int, activityScheduleId: Long = 4444, activityScheduleId2: Long? = 5555) {
    repeat(count) { offset ->
      verify(
        postRequestedFor(urlPathEqualTo("/migrate/allocation"))
          .withRequestBody(matchingJsonPath("activityId", equalTo((activityScheduleId + offset).toString())))
          .withRequestBody(matchingJsonPath("exclusions.size()", equalTo("1")))
          .withRequestBody(matchingJsonPath("exclusions[0].timeSlot", equalTo("AM")))
          .withRequestBody(matchingJsonPath("exclusions[0].monday", equalTo("true")))
          .withRequestBody(matchingJsonPath("exclusions[0].daysOfWeek.length()", equalTo("1")))
          .withRequestBody(matchingJsonPath("exclusions[0].daysOfWeek[0]", containing("MONDAY")))
          .apply {
            activityScheduleId2
              ?.run { withRequestBody(matchingJsonPath("splitRegimeActivityId", equalTo((activityScheduleId2.let { (offset + it).toString() })))) }
              ?: run { withRequestBody(notMatching("splitRegimeActivityId")) }
          },
      )
    }
  }

  fun stubGetActivityCategories() {
    activitiesApi.stubFor(
      get(WireMock.urlEqualTo("/activity-categories")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            """
                [
                  {
                     "id": 1,
                     "code": "SAA_EDUCATION",
                     "name": "Education",
                     "description": "Education"
                  },
                  {
                     "id": 2,
                     "code": "SAA_INDUCTION",
                     "name": "Induction",
                     "description": "Induction"
                  }
                ]
            """.trimIndent(),
          ),
      ),
    )
  }

  fun stubMoveActivityStartDates(
    prisonCode: String = "BXI",
    activityStartDate: LocalDate = LocalDate.now().plusDays(2),
  ) {
    activitiesApi.stubFor(
      post(urlPathEqualTo("/migrate/$prisonCode/move-activity-start-dates"))
        .withQueryParam("activityStartDate", equalTo("$activityStartDate"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody("""["Error1", "Error2"]"""),
        ),
    )
  }

  fun stubMoveActivityStartDatesError(
    prisonCode: String = "BXI",
    activityStartDate: LocalDate = LocalDate.now().plusDays(2),
    status: HttpStatus,
  ) {
    activitiesApi.stubFor(
      post(urlPathEqualTo("/migrate/$prisonCode/move-activity-start-dates"))
        .withQueryParam("activityStartDate", equalTo("$activityStartDate"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody("""{"userMessage":"DPS error"}"""),
        ),
    )
  }

  fun verifyMoveActivityStartDates(
    prisonCode: String = "BXI",
    activityStartDate: LocalDate = LocalDate.now().plusDays(2),
    times: Int = 1,
  ) {
    verify(
      times,
      postRequestedFor(urlPathEqualTo("/migrate/$prisonCode/move-activity-start-dates"))
        .withQueryParam("activityStartDate", equalTo("$activityStartDate")),
    )
  }

  fun createActivitiesCount() = findAll(postRequestedFor(urlMatching("/migrate/activity"))).count()

  fun createAllocationsCount() = findAll(postRequestedFor(urlMatching("/migrate/allocation"))).count()
}
