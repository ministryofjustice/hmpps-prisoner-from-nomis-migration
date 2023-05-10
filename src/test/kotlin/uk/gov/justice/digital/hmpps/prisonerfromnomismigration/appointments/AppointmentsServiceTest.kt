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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ActivitiesApiExtension
import java.time.LocalDate
import java.time.LocalTime

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
      ActivitiesApiExtension.activitiesApi.stubCreateAppointmentForMigration(appointmentInstanceId = APPOINTMENT_INSTANCE_ID)
      runBlocking {
        appointmentsService.createAppointment(
          AppointmentMigrateRequest(
            bookingId = 1234,
            prisonCode = "MDI",
            comment = "Remand added",
            prisonerNumber = "G4803UT",
            internalLocationId = 1234,
            startDate = LocalDate.parse("2021-07-01"),
            startTime = LocalTime.parse("00:01"),
            endTime = LocalTime.parse("00:02"),
            categoryCode = "APP",
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
      )
    }
  }
}
