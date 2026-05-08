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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtMovementIn
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtMovementOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtScheduleOut
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime

@Component
class CourtSchedulerNomisApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetCourtScheduleOut(
    offenderNo: String = "A1234BC",
    eventId: Long = 12345L,
    eventTime: LocalDateTime = yesterday,
    courtCaseId: Long? = null,
    response: CourtScheduleOut = courtScheduleOutResponse(
      startTime = eventTime,
      eventId = eventId,
      courtCaseId = courtCaseId,
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

  fun stubGetCourtMovementOut(
    offenderNo: String = "A1234BC",
    bookingId: Long = 12345L,
    movementSeq: Int = 3,
    movementTime: LocalDateTime = yesterday,
    courtScheduleOutId: Long? = null,
    response: CourtMovementOut = courtMovementOutResponse(
      movementTime = movementTime,
      movementSeq = movementSeq,
      courtScheduleOutId = courtScheduleOutId,
    ),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/movements/$offenderNo/court/movement/out/$bookingId/$movementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetCourtMovementOut(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/movements/.*/court/movement/out/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetCourtMovementIn(
    offenderNo: String = "A1234BC",
    bookingId: Long = 12345L,
    movementSeq: Int = 4,
    movementTime: LocalDateTime = yesterday,
    courtScheduleOutId: Long? = null,
    response: CourtMovementIn = courtMovementInResponse(
      movementSeq = movementSeq,
      movementTime = movementTime,
      courtScheduleOutId = courtScheduleOutId,
    ),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/movements/$offenderNo/court/movement/in/$bookingId/$movementSeq")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetCourtMovementIn(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/movements/.*/court/movement/in/.*")).willReturn(
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
      auditModuleName: String? = "OCCCDCASE",
      courtCaseId: Long? = null,
    ) = CourtScheduleOut(
      bookingId = 12345,
      courtCaseId = courtCaseId,
      eventId = eventId,
      eventDate = startTime.toLocalDate(),
      startTime = startTime,
      eventType = "CRT",
      eventStatus = eventStatus,
      prison = "BXI",
      court = "LEEDMC",
      comment = "court schedule comment",
      userActiveCaseloadId = "MDI",
      audit = NomisAudit(
        createDatetime = now,
        createUsername = "USER",
        auditModuleName = auditModuleName,
      ),
    )

    fun courtMovementOutResponse(
      movementSeq: Int = 3,
      movementTime: LocalDateTime = now,
      courtScheduleOutId: Long? = null,
    ) = CourtMovementOut(
      bookingId = 12345,
      sequence = movementSeq,
      movementDate = movementTime.toLocalDate(),
      movementTime = movementTime,
      movementReason = "CRT",
      fromPrison = "BXI",
      toCourt = "LEEDMC",
      courtScheduleOutId = courtScheduleOutId,
      userActiveCaseloadId = "MDI",
      audit = NomisAudit(
        createDatetime = now,
        createUsername = "USER",
      ),
    )

    fun courtMovementInResponse(
      movementSeq: Int = 4,
      movementTime: LocalDateTime = now,
      courtScheduleOutId: Long? = null,
    ) = CourtMovementIn(
      bookingId = 12345,
      sequence = movementSeq,
      movementDate = movementTime.toLocalDate(),
      movementTime = movementTime,
      movementReason = "CRT",
      toPrison = "BXI",
      fromCourt = "LEEDMC",
      courtScheduleOutId = courtScheduleOutId,
      userActiveCaseloadId = "MDI",
      audit = NomisAudit(
        createDatetime = now,
        createUsername = "USER",
      ),
    )
  }
}
