package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.BookingAlertsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerAlertsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class AlertsNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetAlert(
    bookingId: Long = 123456,
    alertSequence: Long = 1,
    alert: AlertResponse = AlertResponse(
      bookingId = bookingId,
      alertSequence = alertSequence,
      bookingSequence = 10,
      alertCode = CodeDescription("XA", "TACT"),
      type = CodeDescription("X", "Security"),
      date = LocalDate.now(),
      isActive = true,
      isVerified = false,
      audit = NomisAudit(
        createDatetime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        createUsername = "Q1251T",
      ),
    ),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/booking-id/$bookingId/alerts/$alertSequence")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(alert)),
      ),
    )
  }

  fun stubGetAlert(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/prisoners/booking-id/\\d+/alerts/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetAlertsToMigrate(
    offenderNo: String,
    currentAlertCount: Long = 1,
    alert: AlertResponse = AlertResponse(
      bookingId = 1,
      alertSequence = 1,
      bookingSequence = 10,
      alertCode = CodeDescription("XA", "TACT"),
      type = CodeDescription("X", "Security"),
      date = LocalDate.now(),
      isActive = true,
      isVerified = false,
      audit = NomisAudit(
        createDatetime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        createUsername = "Q1251T",
      ),
    ),
  ) {
    val response = PrisonerAlertsResponse(
      latestBookingAlerts = (1..currentAlertCount).map { alert.copy(bookingId = it, alertSequence = 1) },
    )
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/alerts/to-migrate")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubGetAlertsToResynchronise(
    offenderNo: String,
    bookingId: Long,
    currentAlertCount: Long = 1,
    alert: AlertResponse = AlertResponse(
      bookingId = bookingId,
      alertSequence = 1,
      bookingSequence = 10,
      alertCode = CodeDescription("XA", "TACT"),
      type = CodeDescription("X", "Security"),
      date = LocalDate.now(),
      isActive = true,
      isVerified = false,
      audit = NomisAudit(
        createDatetime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        createUsername = "Q1251T",
      ),
    ),
  ) {
    val response = PrisonerAlertsResponse(
      latestBookingAlerts = (1..currentAlertCount).map { alert.copy(bookingId = bookingId, alertSequence = it) },
    )
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/alerts/to-migrate")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetAlertsToMigrate(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/prisoners/.+/alerts/to-migrate")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetAlertsByBookingId(
    bookingId: Long,
    alertCount: Long = 1,
    alert: AlertResponse = AlertResponse(
      bookingId = 1,
      alertSequence = 1,
      bookingSequence = 10,
      alertCode = CodeDescription("XA", "TACT"),
      type = CodeDescription("X", "Security"),
      date = LocalDate.now(),
      isActive = true,
      isVerified = false,
      audit = NomisAudit(
        createDatetime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        createUsername = "Q1251T",
      ),
    ),
  ) {
    val response = BookingAlertsResponse(
      alerts = (1..alertCount).map { alert.copy(bookingId = bookingId, alertSequence = it) },
    )
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/booking-id/$bookingId/alerts")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetPreviousBooking(offenderNo: String, bookingId: Long, oldBookingId: Long = 12345, oldBookingSequence: Long = 2) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/bookings/$bookingId/previous")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            """
              {
                "bookingId": $oldBookingId,
                "bookingSequence": $oldBookingSequence
              }
            """.trimIndent(),
          ),
      ),
    )
  }

  fun stubGetPrisonerDetails(offenderNo: String, prisonerDetails: PrisonerDetails = prisonerDetails()) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(prisonerDetails)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun prisonerDetails(): PrisonerDetails = PrisonerDetails(offenderNo = "A1234KT", bookingId = 1234, location = "MDI", active = true)
