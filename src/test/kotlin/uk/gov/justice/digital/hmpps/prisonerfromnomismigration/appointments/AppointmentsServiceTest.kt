package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments

import com.github.tomakehurst.wiremock.client.WireMock
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.ActivitiesConfiguration
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.model.AppointmentMigrateRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ActivitiesApiExtension
import java.time.LocalDate
import java.time.LocalDateTime

private const val APPOINTMENT_INSTANCE_ID = 1234567L

@SpringAPIServiceTest
@Import(AppointmentsService::class, ActivitiesConfiguration::class)
internal class AppointmentsServiceTest {

  @Autowired
  private lateinit var appointmentsService: AppointmentsService

  @Nested
  @DisplayName("POST /migrate-appointment")
  inner class CreateAppointmentForMigration {
    @BeforeEach
    internal fun setUp() {
      ActivitiesApiExtension.activitiesApi.stubCreateAppointmentForMigration(APPOINTMENT_INSTANCE_ID)
      runBlocking {
        appointmentsService.createAppointment(
          AppointmentMigrateRequest(
            bookingId = 1234,
            prisonCode = "MDI",
            comment = "Remand added",
            prisonerNumber = "G4803UT",
            internalLocationId = 1234,
            startDate = LocalDate.parse("2021-07-01"),
            startTime = "00:01",
            endTime = "00:02",
            categoryCode = "APP",
            isCancelled = false,
            createdBy = "ITAG_USER",
            created = LocalDateTime.parse("2020-01-01T10:00:00"),
            updatedBy = "ITAG_USER2",
            updated = LocalDateTime.parse("2020-02-02T12:00:00"),
          ),
        )
      }
    }

    @Test
    fun `should call api with OAuth2 token`() {
      ActivitiesApiExtension.activitiesApi.verify(
        WireMock.postRequestedFor(WireMock.urlEqualTo("/migrate-appointment"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass data to the api`() {
      ActivitiesApiExtension.activitiesApi.verify(
        WireMock.postRequestedFor(WireMock.urlEqualTo("/migrate-appointment"))
          .withRequestBody(WireMock.matchingJsonPath("bookingId", WireMock.equalTo("1234")))
          .withRequestBody(WireMock.matchingJsonPath("prisonerNumber", WireMock.equalTo("G4803UT")))
          .withRequestBody(WireMock.matchingJsonPath("prisonCode", WireMock.equalTo("MDI")))
          .withRequestBody(WireMock.matchingJsonPath("internalLocationId", WireMock.equalTo("1234")))
          .withRequestBody(WireMock.matchingJsonPath("comment", WireMock.equalTo("Remand added")))
          .withRequestBody(WireMock.matchingJsonPath("startDate", WireMock.equalTo("2021-07-01")))
          .withRequestBody(WireMock.matchingJsonPath("startTime", WireMock.equalTo("00:01")))
          .withRequestBody(WireMock.matchingJsonPath("endTime", WireMock.equalTo("00:02")))
          .withRequestBody(WireMock.matchingJsonPath("categoryCode", WireMock.equalTo("APP")))
          .withRequestBody(WireMock.matchingJsonPath("isCancelled", WireMock.equalTo("false")))
          .withRequestBody(WireMock.matchingJsonPath("createdBy", WireMock.equalTo("ITAG_USER")))
          .withRequestBody(WireMock.matchingJsonPath("created", WireMock.equalTo("2020-01-01T10:00:00")))
          .withRequestBody(WireMock.matchingJsonPath("updatedBy", WireMock.equalTo("ITAG_USER2")))
          .withRequestBody(WireMock.matchingJsonPath("updated", WireMock.equalTo("2020-02-02T12:00:00"))),
      )
    }
  }
}
