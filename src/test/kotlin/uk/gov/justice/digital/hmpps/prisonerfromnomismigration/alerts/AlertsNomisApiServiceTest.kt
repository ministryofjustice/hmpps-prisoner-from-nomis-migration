package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NomisAudit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@SpringAPIServiceTest
@Import(AlertsNomisApiService::class, AlertsConfiguration::class, AlertsNomisApiMockServer::class)
class AlertsNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: AlertsNomisApiService

  @Autowired
  private lateinit var alertsNomisApiMockServer: AlertsNomisApiMockServer

  @Nested
  inner class GetAlert {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsNomisApiMockServer.stubGetAlert(bookingId = 1234567, alertSequence = 3)

      apiService.getAlert(bookingId = 1234567, alertSequence = 3)

      alertsNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      alertsNomisApiMockServer.stubGetAlert(bookingId = 1234567, alertSequence = 3)

      apiService.getAlert(bookingId = 1234567, alertSequence = 3)

      alertsNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoner/booking-id/1234567/alerts/3")),
      )
    }

    @Test
    fun `will return alert`() = runTest {
      alertsNomisApiMockServer.stubGetAlert(
        bookingId = 1234567,
        alertSequence = 3,
        alert = AlertResponse(
          bookingId = 1234567,
          alertSequence = 3,
          alertCode = CodeDescription("CPC", "PPRC"),
          type = CodeDescription("C", "Child Communication Measures"),
          date = LocalDate.parse("2022-07-19"),
          isActive = true,
          isVerified = false,
          audit = NomisAudit(
            createDatetime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            createUsername = "Q1251T",
          ),
        ),
      )

      val alert = apiService.getAlert(bookingId = 1234567, alertSequence = 3)

      assertThat(alert.bookingId).isEqualTo(1234567)
      assertThat(alert.alertSequence).isEqualTo(3)
      assertThat(alert.date).isEqualTo(LocalDate.parse("2022-07-19"))
    }

    @Test
    fun `will throw error when alert does not exist`() = runTest {
      alertsNomisApiMockServer.stubGetAlert(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getAlert(bookingId = 1234567, alertSequence = 3)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      alertsNomisApiMockServer.stubGetAlert(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getAlert(bookingId = 1234567, alertSequence = 4)
      }
    }
  }
}
