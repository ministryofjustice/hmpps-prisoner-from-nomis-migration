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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class AlertsNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetAlert(
    bookingId: Long = 123456,
    alertSequence: Long = 1,
    alert: AlertResponse = alertResponse(bookingId = bookingId, alertSequence = alertSequence),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoner/booking-id/$bookingId/alerts/$alertSequence")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(alert)),
      ),
    )
  }

  fun stubGetAlert(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/prisoner/booking-id/\\d+/alerts/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
}

fun alertResponse(bookingId: Long = 1234567, alertSequence: Long = 3): AlertResponse = AlertResponse(
  bookingId = bookingId,
  alertSequence = alertSequence,
  alertCode = CodeDescription("XA", "TACT"),
  type = CodeDescription("X", "Security"),
  date = LocalDate.now(),
  isActive = true,
  isVerified = false,
  audit = NomisAudit(
    createDatetime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
    createUsername = "Q1251T",
  ),
)
