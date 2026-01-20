package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtOrderResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenceResultCodeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PostPrisonerMergeCaseChanges
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.RecallCustodyDate
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceTermResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.COURT_SENTENCING_PRISONER_IDS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class CourtSentencingNomisApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetCourtCase(
    bookingId: Long = 123456,
    offenderNo: String = "A3864DZ",
    courtCaseId: Long = 3,
    caseIdentifiers: List<CaseIdentifierResponse> = emptyList(),
    sentences: List<SentenceResponse> = emptyList(),
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
      createdDateTime = LocalDateTime.now(),
      createdByUsername = "Q1251T",
      primaryCaseInfoNumber = "caseRef1",
      caseInfoNumbers = caseIdentifiers,
      sentences = sentences,
      sourceCombinedCaseIds = listOf(),
    ),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/sentencing/court-cases/$courtCaseId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetCourtCase(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(WireMock.urlPathMatching("/prisoners/\\S+/sentencing/court-cases/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  // this migration version does not have offenderNo validation
  fun stubGetCourtCasesByOffenderForMigration(
    caseId: Long,
    bookingId: Long = 2,
    offenderNo: String = "AN1",
    courtId: String = "BATHMC",
    caseInfoNumber: String? = "caseRef1",
    caseIdentifiers: List<CaseIdentifierResponse> = emptyList(),
    courtEvents: List<CourtEventResponse> = emptyList(),
    combinedCaseId: Long? = null,
    sentences: List<SentenceResponse> = emptyList(),
    response: List<CourtCaseResponse> = listOf(
      CourtCaseResponse(
        bookingId = bookingId,
        id = caseId,
        offenderNo = offenderNo,
        caseSequence = 22,
        caseStatus = CodeDescription("A", "Active"),
        legalCaseType = CodeDescription("A", "Adult"),
        courtId = "MDI",
        courtEvents = courtEvents,
        offenderCharges = emptyList(),
        createdDateTime = LocalDateTime.now(),
        createdByUsername = "Q1251T",
        primaryCaseInfoNumber = "caseRef1",
        caseInfoNumbers = caseIdentifiers,
        combinedCaseId = combinedCaseId,
        sentences = sentences,
        sourceCombinedCaseIds = listOf(),
      ),
    ),
  ) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/prisoners/$offenderNo/sentencing/court-cases"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }
  fun stubGetCourtCasesByOffenderForMigration(
    offenderNo: String = "AN1",
    response: List<CourtCaseResponse> = listOf(courtCaseResponse()),
  ) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/prisoners/$offenderNo/sentencing/court-cases"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetCourtCases(
    offenderNo: String = "AN1",
    response: List<CourtCaseResponse> = listOf(courtCaseResponse()),
  ) {
    nomisApi.stubFor(
      post(
        urlPathEqualTo("/prisoners/$offenderNo/sentencing/court-cases/get-list"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetCourtCasesByOffenderForMigration(
    offenderNo: String,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/prisoners/{offenderNo}/sentencing/court-cases"),
      )
        .willReturn(
          aResponse()
            .withStatus(status.value())
            .withHeader("Content-Type", "application/json")
            .withBody(NomisApiExtension.jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  // this migration version does not have offenderNo validation
  fun stubGetCourtCaseNoOffenderVersion(
    caseId: Long,
    bookingId: Long = 2,
    offenderNo: String = "G4803UT",
    courtId: String = "BATHMC",
    caseInfoNumber: String? = "caseRef1",
    caseIndentifiers: List<CaseIdentifierResponse> = emptyList(),
    courtEvents: List<CourtEventResponse> = emptyList(),
    combinedCaseId: Long? = null,
    sentences: List<SentenceResponse> = emptyList(),
    response: CourtCaseResponse = CourtCaseResponse(
      bookingId = bookingId,
      id = caseId,
      offenderNo = offenderNo,
      caseSequence = 22,
      caseStatus = CodeDescription("A", "Active"),
      legalCaseType = CodeDescription("A", "Adult"),
      courtId = "MDI",
      courtEvents = courtEvents,
      offenderCharges = emptyList(),
      createdDateTime = LocalDateTime.now(),
      createdByUsername = "Q1251T",
      primaryCaseInfoNumber = "caseRef1",
      caseInfoNumbers = caseIndentifiers,
      combinedCaseId = combinedCaseId,
      sentences = sentences,
      sourceCombinedCaseIds = listOf(),
    ),
  ) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/court-cases/$caseId"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetCourtCaseNoOffenderVersion(
    caseId: Long,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/court-cases/$caseId"),
      )
        .willReturn(
          aResponse()
            .withStatus(status.value())
            .withHeader("Content-Type", "application/json")
            .withBody(NomisApiExtension.jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubGetCourtCasesChangedByMerge(
    offenderNo: String = "AK000KT",
    courtCasesCreated: List<CourtCaseResponse> = emptyList(),
    courtCasesDeactivated: List<CourtCaseResponse> = emptyList(),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/sentencing/court-cases/post-merge"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              jsonMapper.writeValueAsString(
                PostPrisonerMergeCaseChanges(
                  courtCasesCreated = courtCasesCreated,
                  courtCasesDeactivated = courtCasesDeactivated,
                ),
              ),
            ),
        ),
    )
  }

  fun stubGetCourtCasesChangedByMerge(
    offenderNo: String = "AK000KT",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/sentencing/court-cases/post-merge"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(
              jsonMapper.writeValueAsString(error),
            ),
        ),
    )
  }

  fun stubGetCourtAppearance(
    offenderNo: String = "A3864DZ",
    courtAppearanceId: Long = 3,
    courtCaseId: Long? = 2,
    courtId: String = "MDI",
    eventDateTime: LocalDateTime = LocalDateTime.now(),
    nextEventDateTime: LocalDateTime = LocalDateTime.now(),
    courtEventCharges: List<CourtEventChargeResponse> = emptyList(),
    response: CourtEventResponse = CourtEventResponse(
      id = courtAppearanceId,
      offenderNo = offenderNo,
      caseId = courtCaseId,
      courtId = courtId,
      courtEventCharges = courtEventCharges,
      createdDateTime = LocalDateTime.now(),
      createdByUsername = "Q1251T",
      modifiedByUsername = "jbell",
      courtEventType = CodeDescription("CRT", "Court Appearance"),
      outcomeReasonCode = OffenceResultCodeResponse(chargeStatus = "A", code = "4506", description = "Adjournment", dispositionCode = "I", conviction = false),
      eventStatus = CodeDescription("SCH", "Scheduled (Approved)"),
      eventDateTime = eventDateTime,
      courtOrders = emptyList(),
      nextEventDateTime = nextEventDateTime,
    ),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/sentencing/court-appearances/$courtAppearanceId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetCourtAppearance(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(WireMock.urlPathMatching("/prisoners/\\S+/sentencing/court-cases/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetOffenderCharge(
    offenderNo: String = "A3864DZ",
    offenderChargeId: Long = 3,
    offence: OffenceResponse = OffenceResponse(offenceCode = "RI64006", statuteCode = "RI64", description = "Offence description"),
    response: OffenderChargeResponse = OffenderChargeResponse(
      id = offenderChargeId,
      offence = offence,
      mostSeriousFlag = true,
      offenceDate = LocalDate.of(2024, 4, 4),
      plea = CodeDescription("NG", "Not Guilty"),
      resultCode1 = OffenceResultCodeResponse(chargeStatus = "A", code = "1002", description = "Imprisonment", dispositionCode = "F", conviction = false),
      createdByUsername = "msmith",
    ),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/sentencing/offender-charges/$offenderChargeId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetOffenderCharge(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(WireMock.urlPathMatching("/prisoners/\\S+/sentencing/offender-charges/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetCourtEventCharge(
    offenderNo: String = "A3864DZ",
    offenderChargeId: Long = 3,
    courtAppearanceId: Long = 3,
    offenderCharge: OffenderChargeResponse = OffenderChargeResponse(
      id = offenderChargeId,
      offence = OffenceResponse(offenceCode = "RI64006", statuteCode = "RI64", description = "Offender description"),
      mostSeriousFlag = true,
      offenceDate = LocalDate.of(2024, 2, 2),
      resultCode1 = OffenceResultCodeResponse(chargeStatus = "A", code = "1002", description = "Imprisonment", dispositionCode = "F", conviction = false),
      plea = CodeDescription("NG", "Not Guilty"),
      createdByUsername = "msmith",
    ),
    response: CourtEventChargeResponse = CourtEventChargeResponse(
      offenderCharge = offenderCharge,
      mostSeriousFlag = true,
      offenceDate = LocalDate.of(2024, 3, 3),
      plea = CodeDescription("NG", "Not Guilty"),
      eventId = courtAppearanceId,
      resultCode1 = OffenceResultCodeResponse(chargeStatus = "A", code = "1002", description = "Imprisonment", dispositionCode = "F", conviction = false),
      createdByUsername = "msmith",
    ),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/sentencing/court-appearances/$courtAppearanceId/charges/$offenderChargeId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetLastModifiedCourtEventCharge(
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(WireMock.urlPathMatching("/prisoners/\\S+/sentencing/court-event-charges/\\d+/last-modified/")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetCourtEventCharge(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(WireMock.urlPathMatching("/prisoners/\\S+/sentencing/court-appearances/\\d+/charges/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetSentence(
    endpointUsingCase: Boolean = true,
    bookingId: Long = 123456,
    offenderNo: String = "A3864DZ",
    sentenceSequence: Int = 3,
    caseId: Long = 345,
    startDate: LocalDate = LocalDate.now(),
    courtOrder: CourtOrderResponse? = null,
    consecSequence: Int? = null,
    sentenceLevel: String = "IND",
    recallCustodyDate: RecallCustodyDate? = null,
    sentenceTerms: List<SentenceTermResponse> = listOf(
      SentenceTermResponse(
        startDate = LocalDate.now(),
        sentenceTermType = CodeDescription("IMP", "Imprisonment"),
        termSequence = 1,
        years = 1,
        months = 3,
        weeks = 4,
        days = 5,
        lifeSentenceFlag = false,
        prisonId = "OUT",
        createdByUsername = "msmith",
      ),
    ),
    offenderCharges: List<OffenderChargeResponse> = listOf(
      OffenderChargeResponse(
        id = 101,
        chargeStatus = CodeDescription("A", "Active"),
        offenceDate = LocalDate.of(2024, 1, 1),
        resultCode1 = OffenceResultCodeResponse(chargeStatus = "A", code = "1002", description = "Imprisonment", dispositionCode = "F", conviction = false),
        offence = OffenceResponse(
          offenceCode = "AN16094",
          statuteCode = "AN16",
          description = "Act as organiser of flying    without applying for / obtaining permission of CAA",
        ),
        mostSeriousFlag = false,
        createdByUsername = "msmith",
      ),
      OffenderChargeResponse(
        id = 102,
        chargeStatus = CodeDescription("A", "Active"),
        offenceDate = LocalDate.of(2024, 4, 1),
        resultCode1 = OffenceResultCodeResponse(chargeStatus = "A", code = "1002", description = "Imprisonment", dispositionCode = "F", conviction = false),
        offence = OffenceResponse(
          offenceCode = "AN10020",
          statuteCode = "AN10",
          description = "Act in a way likely to cause annoyance / nuisance / injury to others within a Controlled Area of AWE Burghfield",
        ),
        mostSeriousFlag = true,
        createdByUsername = "msmith",
      ),
    ),
    response: SentenceResponse = SentenceResponse(
      bookingId = bookingId,
      sentenceSeq = sentenceSequence.toLong(),
      caseId = caseId,
      courtOrder = courtOrder,
      category = CodeDescription(code = "2003", "2003 Act"),
      calculationType = CodeDescription(code = "ADIMP_ORA", description = "ADIMP_ORA description"),
      offenderCharges = offenderCharges,
      startDate = startDate,
      status = "I",
      sentenceTerms = sentenceTerms,
      createdDateTime = LocalDateTime.now(),
      createdByUsername = "Q1251T",
      prisonId = "MDI",
      fineAmount = BigDecimal.valueOf(1.10),
      consecSequence = consecSequence,
      sentenceLevel = sentenceLevel,
      recallCustodyDate = recallCustodyDate,
      missingCourtOffenderChargeIds = emptyList(),
    ),
  ) {
    val url = if (endpointUsingCase) "/prisoners/$offenderNo/court-cases/$caseId/sentences/$sentenceSequence" else "/prisoners/booking-id/$bookingId/sentences/$sentenceSequence"
    nomisApi.stubFor(
      get(urlEqualTo(url)).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetSentence(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(WireMock.urlPathMatching("/prisoners/\\S+/court-cases/\\d+/sentences/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetSentenceByBooking(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(WireMock.urlPathMatching("/prisoners/booking-id/\\d+/sentences/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetSentenceTerm(
    bookingId: Long = 123456,
    offenderNo: String = "A3864DZ",
    sentenceSequence: Int = 3,
    termSequence: Int = 5,
    startDate: LocalDate = LocalDate.now(),
    response: SentenceTermResponse = SentenceTermResponse(
      startDate = startDate,
      sentenceTermType = CodeDescription("IMP", "Imprisonment"),
      termSequence = 1,
      years = 1,
      months = 3,
      weeks = 4,
      days = 5,
      lifeSentenceFlag = false,
      prisonId = "OUT",
      createdByUsername = "msmith",
    ),
  ) {
    println("\n\n/prisoners/$offenderNo/sentence-terms/booking-id/$bookingId/sentence-sequence/$sentenceSequence/term-sequence/$termSequence")
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/sentence-terms/booking-id/$bookingId/sentence-sequence/$sentenceSequence/term-sequence/$termSequence")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetSentenceTerm(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(WireMock.urlPathMatching("/prisoners/\\S+/sentence-terms/\\S+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubMultipleGetPrisonerIdCounts(totalElements: Long, pageSize: Long) {
    // for each page create a response for each case id starting from 1 up to `totalElements`

    val pages = (totalElements / pageSize) + 1
    (0..pages).forEach { page ->
      val startOffenderId = (page * pageSize) + 1
      val endOffenderId = java.lang.Long.min((page * pageSize) + pageSize, totalElements)
      nomisApi.stubFor(
        get(
          urlPathEqualTo(COURT_SENTENCING_PRISONER_IDS),
        )
          .withQueryParam("page", WireMock.equalTo(page.toString()))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(
                prisonerIdsPagedResponse(
                  totalElements = totalElements,
                  ids = (startOffenderId..endOffenderId).map { "AN$it" },
                  pageNumber = page,
                  pageSize = pageSize,
                ),
              ),
          ),
      )
    }
  }

  fun stubMultipleGetCourtCasesByOffender(intProgression: IntProgression) {
    (intProgression).forEach {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/prisoners/AN$it/sentencing/court-cases"),
        )
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(jsonMapper.writeValueAsString(listOf(courtCaseResponse(caseId = it.toLong())))),
          ),
      )
    }
  }
  fun stubGetCourtCasesByOffender(offenderNo: String) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/prisoners/$offenderNo/sentencing/court-cases"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(listOf(courtCaseResponse(caseId = 1)))),
        ),
    )
  }

  fun stubGetOffenderActiveRecallSentences(bookingId: Long, response: List<SentenceResponse> = listOf()) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/booking-id/$bookingId/sentences/recall")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  private fun courtCaseResponse(
    bookingId: Long = 2,
    offenderNo: String = "G4803UT",
    caseId: Long = 3,
    courtId: String = "BATHMC",
    eventId: Long = 528456562,
    caseInfoNumber: String? = "caseRef1",
    caseIndentifiers: List<CaseIdentifierResponse> = emptyList(),
    sentences: List<SentenceResponse> = emptyList(),
  ): CourtCaseResponse = CourtCaseResponse(
    bookingId = bookingId,
    id = caseId,
    offenderNo = offenderNo,
    caseSequence = 22,
    caseStatus = CodeDescription("A", "Active"),
    legalCaseType = CodeDescription("A", "Adult"),
    courtId = courtId,
    courtEvents = listOf(
      CourtEventResponse(
        id = eventId,
        caseId = caseId,
        offenderNo = "A3864DZ",
        eventDateTime = LocalDateTime.parse("2024-02-01T10:00:00"),
        courtEventType = CodeDescription("CRT", "Court Appearance"),
        eventStatus = CodeDescription("SCH", "Scheduled (Approved)"),
        directionCode = CodeDescription("OUT", "Out"),
        courtId = courtId,
        outcomeReasonCode = OffenceResultCodeResponse(chargeStatus = "A", code = "4506", description = "Adjournment", dispositionCode = "I", conviction = false),
        createdDateTime = LocalDateTime.parse("2024-02-08T14:36:16.485181"),
        createdByUsername = "PRISONER_MANAGER_API",
        courtEventCharges = listOf(
          CourtEventChargeResponse(
            eventId = eventId,
            offencesCount = 1,
            offenceDate = LocalDate.parse("2024-01-02"),
            resultCode1 = OffenceResultCodeResponse(chargeStatus = "A", code = "1081", description = "Detention and Training Order", dispositionCode = "F", conviction = false),
            mostSeriousFlag = false,
            offenderCharge = OffenderChargeResponse(
              id = 3934645,
              offence = OffenceResponse(
                offenceCode = "RR84027",
                statuteCode = "RR84",
                description = "Failing to stop at school crossing (horsedrawn vehicle)",
              ),
              offencesCount = 1,
              offenceDate = LocalDate.parse("2024-01-02"),
              chargeStatus = CodeDescription("A", "Active"),
              resultCode1 = OffenceResultCodeResponse(chargeStatus = "A", code = "1081", description = "Detention and Training Order", dispositionCode = "F", conviction = false),
              mostSeriousFlag = false,
              createdByUsername = "msmith",
            ),
            createdByUsername = "msmith",
            modifiedByUsername = "jbell",
          ),
        ),
        courtOrders = listOf(
          CourtOrderResponse(
            id = 1434174,
            courtDate = LocalDate.parse("2024-02-01"),
            issuingCourt = "ABDRCT",
            orderType = "AUTO",
            orderStatus = "A",
            sentencePurposes = emptyList(),
            eventId = eventId,
          ),
        ),
      ),
    ),
    offenderCharges = listOf(
      OffenderChargeResponse(
        id = 3934645,
        offence = OffenceResponse(
          offenceCode = "RR84027",
          statuteCode = "RR84",
          description = "Failing to stop at school crossing (horsedrawn vehicle)",
        ),
        offencesCount = 1,
        offenceDate = LocalDate.parse("2024-01-02"),
        chargeStatus = CodeDescription("A", "Active"),
        resultCode1 = OffenceResultCodeResponse(chargeStatus = "A", code = "1081", description = "Detention and Training Order", dispositionCode = "F", conviction = false),
        mostSeriousFlag = false,
        createdByUsername = "msmith",
      ),
    ),
    createdDateTime = LocalDateTime.parse("2024-02-08T14:36:16.370572"),
    createdByUsername = "PRISONER_MANAGER_API",
    primaryCaseInfoNumber = caseInfoNumber,
    caseInfoNumbers = caseIndentifiers,
    sentences = sentences,
    sourceCombinedCaseIds = listOf(),
  )

  fun prisonerIdsPagedResponse(
    totalElements: Long = 10,
    ids: List<String> = (0L..10L).map { "AN$it" }.toList(),
    pageSize: Long = 10,
    pageNumber: Long = 0,
  ): String {
    val content = ids.map { """{ "offenderNo": "$it"}""" }
      .joinToString { it }
    return pageContent(content, pageSize, pageNumber, totalElements, ids.size)
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun courtCaseResponse(
  bookingId: Long = 123456,
  offenderNo: String = "A3864DZ",
  courtCaseId: Long = 3,
  caseIdentifiers: List<CaseIdentifierResponse> = emptyList(),
  courtEvents: List<CourtEventResponse> = emptyList(),
  sentences: List<SentenceResponse> = emptyList(),
  caseStatus: CodeDescription = CodeDescription("A", "Active"),
) = CourtCaseResponse(
  bookingId = bookingId,
  id = courtCaseId,
  offenderNo = offenderNo,
  caseSequence = 22,
  caseStatus = caseStatus,
  legalCaseType = CodeDescription("A", "Adult"),
  courtId = "MDI",
  courtEvents = courtEvents,
  offenderCharges = emptyList(),
  createdDateTime = LocalDateTime.now(),
  createdByUsername = "Q1251T",
  primaryCaseInfoNumber = "caseRef1",
  caseInfoNumbers = caseIdentifiers,
  sentences = sentences,
  sourceCombinedCaseIds = listOf(),
)

fun sentenceResponse(bookingId: Long, sentenceSequence: Int, eventId: Long) = SentenceResponse(
  bookingId = bookingId,
  sentenceSeq = sentenceSequence.toLong(),
  caseId = 123,
  courtOrder = buildCourtOrderResponse(eventId = eventId),
  category = CodeDescription(code = "2003", "2003 Act"),
  calculationType = CodeDescription(code = "ADIMP_ORA", description = "ADIMP_ORA description"),
  offenderCharges = emptyList(),
  startDate = LocalDate.now(),
  status = "I",
  sentenceTerms = emptyList(),
  createdDateTime = LocalDateTime.now(),
  createdByUsername = "Q1251T",
  prisonId = "MDI",
  fineAmount = BigDecimal.valueOf(1.10),
  consecSequence = null,
  sentenceLevel = null,
  missingCourtOffenderChargeIds = emptyList(),

)

fun courtEventResponse(eventId: Long) = CourtEventResponse(
  id = eventId,
  caseId = 123,
  offenderNo = "A3864DZ",
  eventDateTime = LocalDateTime.parse("2024-02-01T10:00:00"),
  courtEventType = CodeDescription("CRT", "Court Appearance"),
  eventStatus = CodeDescription("SCH", "Scheduled (Approved)"),
  directionCode = CodeDescription("OUT", "Out"),
  courtId = "SHECC",
  outcomeReasonCode = OffenceResultCodeResponse(chargeStatus = "A", code = "4506", description = "Adjournment", dispositionCode = "I", conviction = false),
  createdDateTime = LocalDateTime.parse("2024-02-08T14:36:16.485181"),
  createdByUsername = "PRISONER_MANAGER_API",
  modifiedByUsername = "jbell",
  courtEventCharges = listOf(
    CourtEventChargeResponse(
      eventId = eventId,
      offencesCount = 1,
      offenceDate = LocalDate.parse("2024-01-02"),
      resultCode1 = OffenceResultCodeResponse(chargeStatus = "A", code = "1081", description = "Detention and Training Order", dispositionCode = "F", conviction = false),
      mostSeriousFlag = false,
      offenderCharge = OffenderChargeResponse(
        id = 3934645,
        offence = OffenceResponse(
          offenceCode = "RR84027",
          statuteCode = "RR84",
          description = "Failing to stop at school crossing (horsedrawn vehicle)",
        ),
        offencesCount = 1,
        offenceDate = LocalDate.parse("2024-01-02"),
        chargeStatus = CodeDescription("A", "Active"),
        resultCode1 = OffenceResultCodeResponse(chargeStatus = "A", code = "1081", description = "Detention and Training Order", dispositionCode = "F", conviction = false),
        mostSeriousFlag = false,
        createdByUsername = "msmith",
      ),
      createdByUsername = "msmith",
    ),
  ),
  courtOrders = listOf(
    CourtOrderResponse(
      id = 1434174,
      courtDate = LocalDate.parse("2024-02-01"),
      issuingCourt = "ABDRCT",
      orderType = "AUTO",
      orderStatus = "A",
      sentencePurposes = emptyList(),
      eventId = eventId,
    ),
  ),
)

fun courtEventChargeResponse(eventId: Long, offenderChargeId: Long) = CourtEventChargeResponse(
  eventId = eventId,
  offencesCount = 1,
  offenceDate = LocalDate.parse("2024-01-02"),
  resultCode1 = OffenceResultCodeResponse(chargeStatus = "A", code = "1081", description = "Detention and Training Order", dispositionCode = "F", conviction = false),
  mostSeriousFlag = false,
  offenderCharge = offenderChargeResponse(offenderChargeId),
  createdByUsername = "msmith",
)

fun offenderChargeResponse(offenderChargeId: Long) = OffenderChargeResponse(
  id = offenderChargeId,
  offence = OffenceResponse(
    offenceCode = "RR84027",
    statuteCode = "RR84",
    description = "Failing to stop at school crossing (horsedrawn vehicle)",
  ),
  offencesCount = 1,
  offenceDate = LocalDate.parse("2024-01-02"),
  chargeStatus = CodeDescription("A", "Active"),
  resultCode1 = OffenceResultCodeResponse(chargeStatus = "A", code = "1081", description = "Detention and Training Order", dispositionCode = "F", conviction = false),
  mostSeriousFlag = false,
  createdByUsername = "msmith",
  modifiedByUsername = "jbell",
)
