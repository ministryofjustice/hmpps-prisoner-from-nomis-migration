package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.History
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.HistoryQuestion
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.HistoryResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.IncidentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.IncidentStatusResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Offender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.OffenderParty
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Question
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Requirement
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Response
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.Staff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffParty
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class IncidentsNomisApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubHealthPing(status: Int) {
    nomisApi.stubFor(
      get("/health/ping").willReturn(
        aResponse().withHeader("Content-Type", "application/json").withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubGetIncident(
    nomisIncidentId: Long = 1234,
    offenderParty: String = "A1234BC",
    status: String = "AWAN",
    reportedDateTime: LocalDateTime = LocalDateTime.parse("2021-07-07T10:35:17"),
    type: String = "ATT_ESC_E",
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/incidents/$nomisIncidentId"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(
              incidentResponse(
                nomisIncidentId = nomisIncidentId,
                offenderParty = offenderParty,
                status = status,
                reportedDateTime = reportedDateTime,
                type = type,
              ),
            ),
        ),
    )
  }

  fun stubGetIncident(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/incidents/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(error),
      ),
    )
  }
  fun stubGetIncidentNotFound(nomisIncidentId: Long = 1234) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/incidents/$nomisIncidentId"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value()),
        ),
    )
  }

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder = this.withBody(jsonMapper.writeValueAsString(body))

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
  fun verify(countMatchingStrategy: CountMatchingStrategy, requestPatternBuilder: RequestPatternBuilder) = nomisApi.verify(countMatchingStrategy, requestPatternBuilder)
}

private fun incidentResponse(
  nomisIncidentId: Long = 1234,
  offenderParty: String = "A1234BC",
  status: String = "AWAN",
  reportedDateTime: LocalDateTime = LocalDateTime.parse("2021-07-07T10:35:17"),
  type: String = "ATT_ESC_E",
): IncidentResponse = IncidentResponse(
  incidentId = nomisIncidentId,
  questionnaireId = 45456,
  title = "This is a test incident",
  description = "On 12/04/2023 approx 16:45 Mr Smith tried to escape.",
  status = IncidentStatusResponse(
    code = status,
    description = "Awaiting Analysis",
    listSequence = 1,
    standardUser = true,
    enhancedUser = true,
  ),
  agency = CodeDescription(
    code = "BXI",
    description = "Brixton",
  ),
  type = type,
  lockedResponse = false,
  incidentDateTime = LocalDateTime.parse("2017-04-12T16:45:00"),
  reportingStaff = Staff(
    username = "FSTAFF_GEN",
    staffId = 485572,
    firstName = "FRED",
    lastName = "STAFF",
  ),
  followUpDate = LocalDate.parse("2017-04-12"),
  createDateTime = LocalDateTime.parse("2021-07-23T10:35:17"),
  createdBy = "JIM SMITH",
  lastModifiedBy = "JIM_ADM",
  lastModifiedDateTime = LocalDateTime.parse("2021-07-23T10:35:17"),
  reportedDateTime = reportedDateTime,
  staffParties =
  listOf(
    StaffParty(
      staff = Staff(
        username = "DJONES",
        staffId = 485577,
        firstName = "DAVE",
        lastName = "JONES",
      ),
      sequence = 1,
      role = CodeDescription("ACT", "Actively Involved"),
      comment = "Dave was hit",
      createDateTime = LocalDateTime.parse("2021-07-23T10:35:17"),
      createdBy = "JIM SMITH",
    ),
  ),
  offenderParties = listOf(
    OffenderParty(
      offender =
      Offender(offenderParty, firstName = "Fred", lastName = "smith"),
      sequence = 1,
      role = CodeDescription("ABS", "Absconder"),
      createDateTime = LocalDateTime.parse("2024-02-06T12:36:00"),
      createdBy = "JIM",
      comment = "This is a comment",
      outcome = CodeDescription("AAA", "SOME OUTCOME"),
    ),
    OffenderParty(
      offender =
      Offender(offenderParty, firstName = "Fred", lastName = "smith"),
      sequence = 2,
      role = CodeDescription("ABS", "Absconder"),
      createDateTime = LocalDateTime.parse("2024-02-06T12:46:00"),
      createdBy = "JIM",
    ),
  ),
  requirements = listOf(
    Requirement(
      agencyId = "ASI",
      staff = Staff(
        username = "DJONES",
        staffId = 485577,
        firstName = "DAVE",
        lastName = "JONES",
      ),
      sequence = 1,
      comment = "Complete the incident report",
      createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
      createdBy = "JSMITH",
      recordedDate = LocalDateTime.parse("2021-08-06T10:01:02"),
    ),
  ),
  questions =
  listOf(
    Question(
      questionId = 1234,
      sequence = 1,
      question = "Was anybody hurt?",
      answers = listOf(
        Response(
          sequence = 1,
          recordingStaff = Staff(
            username = "JSMITH",
            staffId = 485572,
            firstName = "JIM",
            lastName = "SMITH",
          ),
          createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
          createdBy = "JSMITH",
          questionResponseId = null,
          answer = "Yes",
          responseDate = null,
          comment = null,

        ),
      ),
      createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
      createdBy = "JSMITH",
      hasMultipleAnswers = false,
    ),
  ),
  history = listOf(
    History(
      questionnaireId = 1234,
      type = "ATT_ESC_E",
      description = "Escape Attempt",
      incidentChangeDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
      incidentChangeStaff = Staff(
        username = "JSMITH",
        staffId = 485572,
        firstName = "JIM",
        lastName = "SMITH",
      ),
      createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
      createdBy = "JSMITH",
      questions = listOf(
        HistoryQuestion(
          questionId = 1234,
          sequence = 1,
          question = "Was anybody hurt?",
          answers = listOf(
            HistoryResponse(
              responseSequence = 1,
              recordingStaff = Staff(
                username = "JSMITH",
                staffId = 485572,
                firstName = "JIM",
                lastName = "SMITH",
              ),
              questionResponseId = null,
              answer = "Yes",
              responseDate = null,
              comment = null,
            ),
          ),
        ),
      ),
    ),
  ),
)
