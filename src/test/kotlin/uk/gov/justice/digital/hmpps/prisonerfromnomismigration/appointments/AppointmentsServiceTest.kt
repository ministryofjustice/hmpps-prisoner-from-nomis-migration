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

private const val APPOINTMENT_INSTANCE_ID = 1234567L

@SpringAPIServiceTest
@Import(AppointmentsService::class, ActivitiesConfiguration::class)
internal class AppointmentsServiceTest {

  @Autowired
  private lateinit var appointmentsService: AppointmentsService

  @Nested
  @DisplayName("POST /appointments/TODO")
  inner class CreateAppointmentForMigration {
    @BeforeEach
    internal fun setUp() {
      ActivitiesApiExtension.activitiesApi.stubCreateAppointmentForMigration(appointmentInstanceId = APPOINTMENT_INSTANCE_ID)
      runBlocking {
        appointmentsService.createAppointment(
          CreateAppointmentRequest(
            bookingId = 1234,
            comment = "Remand added",
            offenderNo = "G4803UT",
            startDateTime = LocalDate.parse("2021-07-01").atStartOfDay(),
            endDateTime = LocalDate.parse("2021-07-01").atStartOfDay(),
            subtype = "APP",
            status = "SCH",
          ),
        )
      }
    }

    @Test
    fun `should call api with OAuth2 token`() {
      ActivitiesApiExtension.activitiesApi.verify(
        WireMock.postRequestedFor(WireMock.urlEqualTo("/appointments/TODO"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass data to the api`() {
      ActivitiesApiExtension.activitiesApi.verify(
        WireMock.postRequestedFor(WireMock.urlEqualTo("/appointments/TODO"))
          .withRequestBody(WireMock.matchingJsonPath("bookingId", WireMock.equalTo("1234")))
          .withRequestBody(WireMock.matchingJsonPath("offenderNo", WireMock.equalTo("G4803UT")))
          .withRequestBody(WireMock.matchingJsonPath("comment", WireMock.equalTo("Remand added")))
          .withRequestBody(WireMock.matchingJsonPath("startDateTime", WireMock.equalTo("2021-07-01T00:00:00")))
          .withRequestBody(WireMock.matchingJsonPath("endDateTime", WireMock.equalTo("2021-07-01T00:00:00")))
          .withRequestBody(WireMock.matchingJsonPath("subtype", WireMock.equalTo("APP")))
          .withRequestBody(WireMock.matchingJsonPath("status", WireMock.equalTo("SCH"))),
      )
    }
  }
}
