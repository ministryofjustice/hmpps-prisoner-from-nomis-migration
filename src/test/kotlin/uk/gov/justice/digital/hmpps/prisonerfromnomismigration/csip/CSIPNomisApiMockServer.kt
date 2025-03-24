package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Actions
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Attendee
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CSIPFactorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CSIPIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CSIPResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Decision
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.InterviewDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.InvestigationDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Offender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Plan
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ReportDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Review
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SaferCustodyScreening
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class CSIPNomisApiMockServer(private val objectMapper: ObjectMapper) {
  companion object {
    fun nomisCSIPReport(nomisCSIPId: Long = 1234) = CSIPResponse(
      id = nomisCSIPId,
      offender = Offender("A1234BC", firstName = "Fred", lastName = "Smith"),
      bookingId = 1214478L,
      type = CodeDescription(code = "INT", description = "Intimidation"),
      location = CodeDescription(code = "LIB", description = "Library"),
      areaOfWork = CodeDescription(code = "EDU", description = "Education"),
      reportedDate = LocalDate.parse("2024-04-04"),
      reportedBy = "JIM_ADM",
      proActiveReferral = true,
      staffAssaulted = true,
      staffAssaultedName = "Fred Jones",
      reportDetails = ReportDetails(
        factors = listOf(nomisCSIPFactor()),
        saferCustodyTeamInformed = false,
        referralComplete = true,
        referralCompletedBy = "JIM_ADM",
        referralCompletedByDisplayName = "",
        referralCompletedDate = LocalDate.parse("2024-04-04"),
        involvement = CodeDescription(code = "PER", description = "Perpetrator"),
        concern = "There was a worry about the offender",
        knownReasons = "known reasons details go in here",
        otherInformation = "other information goes in here",
        releaseDate = LocalDate.parse("2026-06-06"),
      ),
      saferCustodyScreening = SaferCustodyScreening(
        outcome = CodeDescription(
          code = "CUR",
          description = "Progress to CSIP",
        ),
        recordedBy = "FRED_ADM",
        recordedByDisplayName = "Fred Admin",
        recordedDate = LocalDate.parse("2024-04-08"),
        reasonForDecision = "There is a reason for the decision - it goes here",
      ),
      investigation = InvestigationDetails(
        staffInvolved = "some people",
        evidenceSecured = "A piece of pipe",
        reasonOccurred = "bad behaviour",
        usualBehaviour = "Good person",
        trigger = "missed meal",
        protectiveFactors = "ensure taken to canteen",
        interviews = listOf(
          InterviewDetails(
            id = 3343,
            interviewee = "Bill Black",
            date = LocalDate.parse("2024-06-06"),
            role = CodeDescription(code = "WITNESS", description = "Witness"),
            createDateTime = LocalDateTime.parse("2024-04-04T15:12:32.00462"),
            createdBy = "AA_ADM",
            comments = "Saw a pipe in his hand",
            lastModifiedDateTime = LocalDateTime.parse("2024-08-12T11:32:15"),
            lastModifiedBy = "BB_ADM",
          ),
        ),
      ),
      decision = Decision(
        actions = Actions(
          openCSIPAlert = false,
          nonAssociationsUpdated = true,
          observationBook = true,
          unitOrCellMove = false,
          csraOrRsraReview = false,
          serviceReferral = true,
          simReferral = false,
        ),
        decisionOutcome = CodeDescription(code = "OPE", description = "Progress to Investigation"),
        recordedBy = "FRED_ADM",
        recordedByDisplayName = "Fred Admin",
        recordedDate = LocalDate.parse("2024-04-08"),
        otherDetails = "Some other info here",
        conclusion = "Offender needs help",
        signedOffRole = CodeDescription("CUSTMAN", description = "Custodial Manager"),

      ),
      plans = listOf(
        Plan(
          id = 65,
          identifiedNeed = "they need help",
          intervention = "dd",
          createdDate = LocalDate.parse("2024-04-16"),
          targetDate = LocalDate.parse("2024-08-20"),
          closedDate = LocalDate.parse("2024-04-17"),
          progression = "there was some improvement",
          referredBy = "Jason",
          createDateTime = LocalDateTime.parse("2024-03-16T11:32:15"),
          createdBy = "PPLAN",
        ),
      ),
      reviews = listOf(
        Review(
          id = 67,
          reviewSequence = 1,
          attendees = listOf(
            Attendee(
              id = 221,
              name = "same jones",
              role = "person",
              attended = true,
              contribution = "talked about things",
              createDateTime = LocalDateTime.parse("2024-08-20T10:33:48.946787"),
              createdBy = "DBULL_ADM",
            ),
          ),
          remainOnCSIP = true,
          csipUpdated = false,
          caseNote = false,
          closeCSIP = true,
          peopleInformed = false,
          closeDate = LocalDate.parse("2024-04-16"),
          recordedDate = LocalDate.parse("2024-04-01"),
          createdBy = "FJAMES",
          createDateTime = LocalDateTime.parse("2024-04-01T10:00:00"),
          recordedBy = "JSMITH",
          recordedByDisplayName = "JOHN SMITH",
        ),
      ),
      documents = listOf(),
      createDateTime = LocalDateTime.parse("2024-04-01T10:32:12.867081"),
      createdBy = "JSMITH",
      originalAgencyId = "MDI",
      logNumber = "ASI-001",
      incidentDate = LocalDate.parse("2024-06-12"),
      incidentTime = "10:32:12",
      caseManager = "C Jones",
      planReason = "helper",
      firstCaseReviewDate = LocalDate.parse("2024-04-15"),
    )

    fun nomisCSIPReportMinimalData(
      nomisCSIPId: Long = 1234,
      reasonOccurred: String? = null,
      interviews: List<InterviewDetails>? = listOf(),
      conclusion: String? = null,
      openCSIPAlert: Boolean = false,
    ) = CSIPResponse(
      id = nomisCSIPId,
      offender = Offender("A1234BC", firstName = "Fred", lastName = "Smith"),
      bookingId = 1214478L,
      originalAgencyId = "MDI",
      logNumber = "ASI-001",
      incidentDate = LocalDate.parse("2024-06-12"),
      type = CodeDescription(code = "INT", description = "Intimidation"),
      location = CodeDescription(code = "LIB", description = "Library"),
      areaOfWork = CodeDescription(code = "EDU", description = "Education"),
      reportedDate = LocalDate.parse("2024-04-04"),
      reportedBy = "JIM_ADM",
      proActiveReferral = false,
      staffAssaulted = false,
      reportDetails = ReportDetails(
        factors = listOf(),
        saferCustodyTeamInformed = false,
        referralComplete = false,
      ),
      saferCustodyScreening = SaferCustodyScreening(),
      investigation = InvestigationDetails(
        reasonOccurred = reasonOccurred,
        interviews = interviews,
      ),
      decision = Decision(
        conclusion = conclusion,
        actions = Actions(
          openCSIPAlert = openCSIPAlert,
          nonAssociationsUpdated = false,
          observationBook = false,
          unitOrCellMove = false,
          csraOrRsraReview = false,
          serviceReferral = false,
          simReferral = false,
        ),
      ),
      plans = listOf(),
      reviews = listOf(),
      createDateTime = LocalDateTime.parse("2024-04-01T10:32:12.867081"),
      createdBy = "JSMITH",
    )

    fun nomisCSIPFactor(nomisCSIPFactorId: Long = 43) = CSIPFactorResponse(
      id = nomisCSIPFactorId,
      type = CodeDescription(code = "BUL", description = "Bullying"),
      comment = "Offender causes trouble",
      createDateTime = LocalDateTime.parse("2024-04-01T10:00:00"),
      createdBy = "CFACTOR",
    )
  }

  fun stubHealthPing(status: Int) {
    nomisApi.stubFor(
      get("/health/ping").willReturn(
        aResponse().withHeader("Content-Type", "application/json").withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubGetCSIP(nomisCSIPId: Long = 1234) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/csip/$nomisCSIPId"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(nomisCSIPReport(nomisCSIPId)),
        ),
    )
  }

  fun stubGetCSIPWithMinimalData(nomisCSIPId: Long = 1234) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/csip/$nomisCSIPId"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(nomisCSIPReportMinimalData(nomisCSIPId)),
        ),
    )
  }

  fun stubGetCSIP(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/csip/[0-9]+"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(error),
        ),
    )
  }

  fun stubGetCSIPFactor(nomisCSIPFactorId: Long = 43) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/csip/factors/$nomisCSIPFactorId"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(nomisCSIPFactor(nomisCSIPFactorId)),
        ),
    )
  }
  fun stubGetCSIPFactor(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/csip/factors/[0-9]+"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(error),
        ),
    )
  }

  fun stubGetCSIPIdsForBooking(bookingId: Long = 2345) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/csip/booking/$bookingId"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(listOf(CSIPIdResponse(1234), CSIPIdResponse(5678))),
        ),
    )
  }
  fun stubGetCSIPIdsForBookingNoCsips(bookingId: Long = 2345) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/csip/booking/$bookingId"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(listOf<CSIPIdResponse>()),
        ),
    )
  }
  fun stubGetCSIPIdsForBooking(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/csip/booking/[0-9]+"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(error),
        ),
    )
  }

  fun verify(countMatchingStrategy: CountMatchingStrategy, requestPatternBuilder: RequestPatternBuilder) = nomisApi.verify(countMatchingStrategy, requestPatternBuilder)
  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder = this.withBody(objectMapper.writeValueAsString(body))
}
