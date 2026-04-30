package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtScheduleOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime

@Component
class CourtNomisApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetCourtScheduleOut(
    offenderNo: String = "A1234BC",
    eventId: Long = 12345L,
    eventTime: LocalDateTime = yesterday,
    response: CourtScheduleOut = courtScheduleOutResponse(
      startTime = eventTime,
      eventId = eventId,
    ),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/movements/$offenderNo/court/schedule/out/$eventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetCourtScheduleOut(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/movements/.*/court/schedule/out/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)

  companion object {
    private val now = LocalDateTime.now()
    private val yesterday = now.minusDays(1)

    fun courtScheduleOutResponse(
      eventId: Long = 1,
      eventStatus: String = "SCH",
      startTime: LocalDateTime = now,
    ) = CourtScheduleOut(
      bookingId = 12345,
      eventId = eventId,
      eventDate = startTime.toLocalDate(),
      startTime = startTime,
      eventType = "CRT",
      eventStatus = eventStatus,
      prison = "BXI",
      comment = "court schedule comment",
      audit = NomisAudit(
        createDatetime = now,
        createUsername = "USER",
      ),
    )
  }
}
