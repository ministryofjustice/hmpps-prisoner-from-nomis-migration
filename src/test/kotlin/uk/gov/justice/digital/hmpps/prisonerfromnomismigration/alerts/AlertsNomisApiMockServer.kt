package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.min

@Component
class AlertsNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetAlert(
    bookingId: Long = 123456,
    alertSequence: Long = 1,
    alert: AlertResponse = AlertResponse(
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
    ),
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

  fun stubGetAlertIds(totalElements: Long = 20, pageSize: Long = 20, bookingId: Long = 123456, offenderNo: String = "A1234KT") {
    val content: List<AlertIdResponse> = (1..min(pageSize, totalElements)).map {
      AlertIdResponse(
        bookingId = bookingId,
        alertSequence = it,
        offenderNo = offenderNo,
      )
    }
    nomisApi.stubFor(
      get(urlPathEqualTo("/alerts/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            pageContent(
              objectMapper = objectMapper,
              content = content,
              pageSize = pageSize,
              pageNumber = 0,
              totalElements = totalElements,
              size = pageSize.toInt(),
            ),
          ),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
}
