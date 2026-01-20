package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerAlertsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class AlertsNomisApiMockServer(private val jsonMapper: JsonMapper) {
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
        createDatetime = LocalDateTime.now(),
        createUsername = "Q1251T",
      ),
    ),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/booking-id/$bookingId/alerts/$alertSequence")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(alert)),
      ),
    )
  }

  fun stubGetAlert(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/prisoners/booking-id/\\d+/alerts/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
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
        createDatetime = LocalDateTime.now(),
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
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetPrisonerDetails(offenderNo: String, prisonerDetails: PrisonerDetails = prisonerDetails()) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(prisonerDetails)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun prisonerDetails(): PrisonerDetails = PrisonerDetails(offenderNo = "A1234KT", offenderId = 5678, bookingId = 1234, location = "MDI", active = true)
