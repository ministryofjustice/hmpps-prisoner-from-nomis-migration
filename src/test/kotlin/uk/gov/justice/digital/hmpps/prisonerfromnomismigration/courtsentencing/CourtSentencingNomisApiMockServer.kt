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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CaseIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtEventChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtOrderResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SentenceResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SentenceTermResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class CourtSentencingNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubGetCourtCase(
    bookingId: Long = 123456,
    offenderNo: String = "A3864DZ",
    courtCaseId: Long = 3,
    caseIndentifiers: List<CaseIdentifierResponse> = emptyList(),
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
      primaryCaseInfoNumber = "caseRef1",
      caseInfoNumbers = caseIndentifiers,
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

  // this migration version does not have offenderNo validation
  fun stubGetCourtCaseForMigration(
    caseId: Long,
    bookingId: Long = 2,
    offenderNo: String = "G4803UT",
    courtId: String = "BATHMC",
    caseInfoNumber: String? = "caseRef1",
    caseIndentifiers: List<CaseIdentifierResponse> = emptyList(),
    courtEvents: List<CourtEventResponse> = emptyList(),
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
      createdDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
      createdByUsername = "Q1251T",
      lidsCaseNumber = 1,
      primaryCaseInfoNumber = "caseRef1",
      caseInfoNumbers = caseIndentifiers,
    ),
  ) {
    nomisApi.stubFor(
      get(
        WireMock.urlPathEqualTo("/court-cases/$caseId"),
      )
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetCourtCaseForMigration(
    caseId: Long,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(
        WireMock.urlPathEqualTo("/court-cases/$caseId"),
      )
        .willReturn(
          aResponse()
            .withStatus(status.value())
            .withHeader("Content-Type", "application/json")
            .withBody(NomisApiExtension.objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubGetCourtAppearance(
    offenderNo: String = "A3864DZ",
    courtAppearanceId: Long = 3,
    courtCaseId: Long? = 2,
    courtId: String = "MDI",
    eventDateTime: String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
    nextEventDateTime: String = LocalDateTime.now().plusWeeks(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
    response: CourtEventResponse = CourtEventResponse(
      id = courtAppearanceId,
      offenderNo = offenderNo,
      caseId = courtCaseId,
      courtId = courtId,
      courtEventCharges = emptyList(),
      createdDateTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
      createdByUsername = "Q1251T",
      courtEventType = CodeDescription("CRT", "Court Appearance"),
      outcomeReasonCode = CodeDescription("4506", "Adjournment"),
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

  fun stubGetLastModifiedCourtEventCharge(
    offenderNo: String = "A3864DZ",
    offenderChargeId: Long = 3,
    courtAppearanceId: Long = 3,
    response: CourtEventChargeResponse = CourtEventChargeResponse(
      offenderCharge = OffenderChargeResponse(
        id = offenderChargeId,
        offence = OffenceResponse(offenceCode = "RI64006", statuteCode = "RI64", description = "Offender description"),
        mostSeriousFlag = true,
        offenceDate = LocalDate.now(),
        plea = CodeDescription("NG", "Not Guilty"),
      ),
      mostSeriousFlag = true,
      offenceDate = LocalDate.now(),
      plea = CodeDescription("NG", "Not Guilty"),
      eventId = courtAppearanceId,
    ),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/sentencing/court-event-charges/$offenderChargeId/last-modified")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
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
          description = "Act as organiser of flying    without applying for / obtaining permission of CAA",
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

  fun stubMultipleGetCourtCaseIdCounts(totalElements: Long, pageSize: Long) {
    // for each page create a response for each case id starting from 1 up to `totalElements`

    val pages = (totalElements / pageSize) + 1
    (0..pages).forEach { page ->
      val startCaseId = (page * pageSize) + 1
      val endCaseId = java.lang.Long.min((page * pageSize) + pageSize, totalElements)
      nomisApi.stubFor(
        get(
          WireMock.urlPathEqualTo("/court-cases/ids"),
        )
          .withQueryParam("page", WireMock.equalTo(page.toString()))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(
                courtCaseIdsPagedResponse(
                  totalElements = totalElements,
                  caseIds = (startCaseId..endCaseId).map { it },
                  pageNumber = page,
                  pageSize = pageSize,
                ),
              ),
          ),
      )
    }
  }

  fun stubMultipleGetCourtCases(intProgression: IntProgression) {
    (intProgression).forEach {
      nomisApi.stubFor(
        get(
          WireMock.urlPathEqualTo("/court-cases/$it"),
        )
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(objectMapper.writeValueAsString(courtCaseResponse(caseId = it.toLong()))),
          ),
      )
    }
  }

  private fun courtCaseResponse(
    bookingId: Long = 2,
    offenderNo: String = "G4803UT",
    caseId: Long = 3,
    courtId: String = "BATHMC",
    caseInfoNumber: String? = "caseRef1",
    caseIndentifiers: List<CaseIdentifierResponse> = emptyList(),
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
        id = 528456562,
        caseId = caseId,
        offenderNo = "A3864DZ",
        eventDateTime = "2024-02-01T10:00:00",
        courtEventType = CodeDescription("CRT", "Court Appearance"),
        eventStatus = CodeDescription("SCH", "Scheduled (Approved)"),
        directionCode = CodeDescription("OUT", "Out"),
        courtId = courtId,
        outcomeReasonCode = CodeDescription("4506", "Adjournment"),
        createdDateTime = "2024-02-08T14:36:16.485181",
        createdByUsername = "PRISONER_MANAGER_API",
        courtEventCharges = listOf(
          CourtEventChargeResponse(
            eventId = 528456562,
            offencesCount = 1,
            offenceDate = LocalDate.parse("2024-01-02"),
            resultCode1 = CodeDescription("1081", "Detention and Training Order"),
            resultCode1Indicator = "F",
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
              resultCode1 = CodeDescription("1081", "Detention and Training Order"),
              resultCode1Indicator = "F",
              mostSeriousFlag = false,
            ),
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
        resultCode1 = CodeDescription("1081", "Detention and Training Order"),
        resultCode1Indicator = "F",
        mostSeriousFlag = false,
      ),
    ),
    createdDateTime = "2024-02-08T14:36:16.370572",
    createdByUsername = "PRISONER_MANAGER_API",
    lidsCaseNumber = 1,
    primaryCaseInfoNumber = caseInfoNumber,
    caseInfoNumbers = caseIndentifiers,
  )

  private fun courtCaseResponse3(
    bookingId: Long = 2,
    offenderNo: String = "G4803UT",
    caseId: Long = 3,
    courtId: String = "BATHMC",
    caseInfoNumber: String? = "caseRef1",
    caseIndentifiers: List<CaseIdentifierResponse> = emptyList(),
  ): String {
    // language=JSON
    return """
{

  "courtEvents": [
    {
     
      "createdDateTime": "2024-02-08T14:36:16.485181",
      "createdByUsername": "PRISONER_MANAGER_API",
      "courtEventCharges": [
        {
          "eventId": 528456562,
          "offenderCharge": {
            "id": 3934645,
            "offence": {
              "offenceCode": "RR84027",
              "statuteCode": "RR84",
              "description": "Failing to stop at school crossing (horsedrawn vehicle)"
            },
            "offencesCount": 1,
            "offenceDate": "2024-01-02",
            "chargeStatus": {
              "code": "A",
              "description": "Active"
            },
            "resultCode1": {
              "code": "1081",
              "description": "Detention and Training Order"
            },
            "resultCode1Indicator": "F",
            "mostSeriousFlag": false,
            "lidsOffenceNumber": 3
          },
          "offencesCount": 1,
          "offenceDate": "2024-01-02",
          "resultCode1": {
            "code": "1081",
            "description": "Detention and Training Order"
          },
          "resultCode1Indicator": "F",
          "mostSeriousFlag": false
        }
      ],
      "courtOrders": [
        {
          "id": 1434174,
          "courtDate": "2024-02-01",
          "issuingCourt": "ABDRCT",
          "orderType": "AUTO",
          "orderStatus": "A",
          "sentencePurposes": []
        }
      ]
    }
  ],
  "caseInfoNumbers": [],
  "offenderCharges": [
    {
      "id": 3934645,
      "offence": {
        "offenceCode": "RR84027",
        "statuteCode": "RR84",
        "description": "Failing to stop at school crossing (horsedrawn vehicle)"
      },
      "offencesCount": 1,
      "offenceDate": "2024-01-02",
      "chargeStatus": {
        "code": "A",
        "description": "Active"
      },
      "resultCode1": {
        "code": "1081",
        "description": "Detention and Training Order"
      },
      "resultCode1Indicator": "F",
      "mostSeriousFlag": false,
      "lidsOffenceNumber": 3
    },
    {
      "id": 3934646,
      "offence": {
        "offenceCode": "RR84028",
        "statuteCode": "RR28",
        "description": "Failing to stop at school crossing (horsedrawn vehicle)"
      },
      "offencesCount": 1,
      "offenceDate": "2024-01-02",
      "chargeStatus": {
        "code": "A",
        "description": "Active"
      },
      "resultCode1": {
        "code": "1081",
        "description": "Detention and Training Order"
      },
      "resultCode1Indicator": "F",
      "mostSeriousFlag": false,
      "lidsOffenceNumber": 3
    }
  ]
}
    """.trimIndent()
  }

  fun courtCaseIdsPagedResponse(
    totalElements: Long = 10,
    caseIds: List<Long> = (0L..10L).toList(),
    pageSize: Long = 10,
    pageNumber: Long = 0,
  ): String {
    val content = caseIds.map { """{ "caseId": $it}""" }
      .joinToString { it }
    return pageContent(content, pageSize, pageNumber, totalElements, caseIds.size)
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
