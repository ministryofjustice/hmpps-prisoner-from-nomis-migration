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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtOrderResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SentenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SentenceTermResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
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
    courtCaseId: Long? = 2,
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

  fun stubGetOffenderCharge(
    offenderNo: String = "A3864DZ",
    offenderChargeId: Long = 3,
    response: OffenderChargeResponse = OffenderChargeResponse(
      id = offenderChargeId,
      offence = OffenceResponse(offenceCode = "RI64006", statuteCode = "RI64", description = "Offender description"),
      mostSeriousFlag = true,
      offenceDate = LocalDate.now(),
      plea = CodeDescription("NG", "Not Guilty"),
    ),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/sentencing/offender-charges/$offenderChargeId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetOffenderCharge(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(WireMock.urlPathMatching("/prisoners/\\S+/sentencing/offender-charges/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetSentence(
    bookingId: Long = 123456,
    offenderNo: String = "A3864DZ",
    sentenceSequence: Int = 3,
    caseId: Long = 345,
    startDate: LocalDate = LocalDate.now(),
    courtOrder: CourtOrderResponse? = null,
    sentenceTerms: List<SentenceTermResponse> = listOf(
      SentenceTermResponse(
        startDate = LocalDate.now(),
        sentenceTermType = CodeDescription("S", "Term type"),
        termSequence = 1,
        years = 1,
        months = 3,
      ),
    ),
    offenderCharges: List<OffenderChargeResponse> = listOf(
      OffenderChargeResponse(
        id = 101,
        chargeStatus = CodeDescription("A", "Active"),
        offenceDate = LocalDate.of(2024, 1, 1),
        resultCode1 = CodeDescription("1002", "Imprisonment"),
        offence = OffenceResponse(
          offenceCode = "AN16094",
          statuteCode = "AN16",
          description = "Act as organiser of flying display without applying for / obtaining permission of CAA",
        ),
        resultCode1Indicator = "F",
        mostSeriousFlag = false,
      ),
      OffenderChargeResponse(
        id = 102,
        chargeStatus = CodeDescription("A", "Active"),
        offenceDate = LocalDate.of(2024, 4, 1),
        resultCode1 = CodeDescription("1002", "Imprisonment"),
        offence = OffenceResponse(
          offenceCode = "AN10020",
          statuteCode = "AN10",
          description = "Act in a way likely to cause annoyance / nuisance / injury to others within a Controlled Area of AWE Burghfield",
        ),
        resultCode1Indicator = "F",
        mostSeriousFlag = true,
      ),
    ),
    response: SentenceResponse = SentenceResponse(
      bookingId = bookingId,
      sentenceSeq = sentenceSequence.toLong(),
      caseId = caseId,
      courtOrder = courtOrder,
      category = CodeDescription(code = "2003", "2003 Act"),
      calculationType = "ADIMP_ORA",
      offenderCharges = offenderCharges,
      startDate = startDate,
      status = "I",
      sentenceTerms = sentenceTerms,
      createdDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
      createdByUsername = "Q1251T",
    ),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/booking-id/$bookingId/sentencing/sentence-sequence/$sentenceSequence")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetSentence(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(WireMock.urlPathMatching("/prisoners/booking-id/\\S+/sentencing/sentence-sequence/\\d+")).willReturn(
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
