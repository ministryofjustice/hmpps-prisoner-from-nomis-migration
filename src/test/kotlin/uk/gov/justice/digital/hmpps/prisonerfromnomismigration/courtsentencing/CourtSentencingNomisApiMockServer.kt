package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class CourtSentencingNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetCourtCase(
    bookingId: Long = 123456,
    offenderNo: String = "A3864DZ",
    courtCaseId: Long = 3,
    response: CourtCaseResponse = CourtCaseResponse(
      bookingId = bookingId,
      id = courtCaseId,
      offenderNo = offenderNo,
      caseSequence = 22,
      caseStatus = CodeDescription("A", "Active"),
      legalCaseType = CodeDescription("A", "Adult"),
      courtId = "MDI",
      courtEvents = emptyList(),
      offenderCharges = emptyList(),
      createdDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
      createdByUsername = "Q1251T",
      lidsCaseNumber = 1,
    ),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/sentencing/court-cases/$courtCaseId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetCourtCase(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(WireMock.urlPathMatching("/prisoners/\\S+/sentencing/court-cases/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetCourtAppearance(
    offenderNo: String = "A3864DZ",
    courtAppearanceId: Long = 3,
    courtCaseId: Long = 2,
    response: CourtEventResponse = CourtEventResponse(
      id = courtAppearanceId,
      offenderNo = offenderNo,
      caseId = courtCaseId,
      courtId = "MDI",
      courtEventCharges = emptyList(),
      createdDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
      createdByUsername = "Q1251T",
      courtEventType = CodeDescription("CRT", "Court Appearance"),
      outcomeReasonCode = CodeDescription("4506", "Adjournment"),
      eventStatus = CodeDescription("SCH", "Scheduled (Approved)"),
      eventDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
      courtOrders = emptyList(),
    ),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/sentencing/court-appearances/$courtAppearanceId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetCourtAppearance(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(WireMock.urlPathMatching("/prisoners/\\S+/sentencing/court-cases/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
