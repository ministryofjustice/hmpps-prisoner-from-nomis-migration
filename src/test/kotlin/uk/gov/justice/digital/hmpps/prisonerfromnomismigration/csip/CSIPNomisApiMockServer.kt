package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Actions
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPFactorResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Decision
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.InvestigationDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Offender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Plan
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.ReportDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Review
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.SaferCustodyScreening
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.lang.Long.min
import java.time.LocalDate

@Component
class CSIPNomisApiMockServer(private val objectMapper: ObjectMapper) {
  companion object {
    const val CSIP_ID_URL = "/csip/ids"

    fun nomisCSIPReport(nomisCSIPId: Long = 1234) =
      CSIPResponse(
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
          referralCompletedDate = LocalDate.parse("2024-04-04"),
          involvement = CodeDescription(code = "PER", description = "Perpetrator"),
          concern = "There was a worry about the offender",
          knownReasons = "known reasons details go in here",
          otherInformation = "other information goes in here",
        ),
        saferCustodyScreening = SaferCustodyScreening(
          outcome = CodeDescription(
            code = "CUR",
            description = "Progress to CSIP",
          ),
          recordedBy = "FRED_ADM",
          recordedDate = LocalDate.parse("2024-04-08"),
          reasonForDecision = "There is a reason for the decision - it goes here",
        ),
        investigation = InvestigationDetails(),
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
          decisionOutcome = CodeDescription(code = "CUR", description = "Progress to CSIP"),
          recordedBy = "FRED_ADM",
          recordedByDisplayName = "Fred Admin",
          recordedDate = LocalDate.parse("2024-04-08"),
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
            createDateTime = "2024-04-01T10:00:00",
            createdBy = "JSMITH",
          ),
        ),
        reviews = listOf(
          Review(
            id = 65,
            reviewSequence = 1,
            attendees = listOf(),
            remainOnCSIP = true,
            csipUpdated = false,
            caseNote = false,
            closeCSIP = true,
            peopleInformed = false,
            closeDate = LocalDate.parse("2024-04-16"),
            createDateTime = "2024-04-01T10:00:00",
            createdBy = "JSMITH",
            createdByDisplayName = "JOHN SMITH",
          ),
        ),
        documents = listOf(),
        createDateTime = "2024-04-01T10:32:12.867081",
        createdBy = "JSMITH",
        originalAgencyId = "MDI",
        logNumber = "ASI-001",
        incidentDate = LocalDate.parse("2024-06-12"),
        incidentTime = "10:32:12",
        caseManager = "Jim Smith",
        planReason = "helper",
        firstCaseReviewDate = LocalDate.parse("2024-04-15"),
      )

    fun nomisCSIPFactor(nomisCSIPFactorId: Long = 43) =
      CSIPFactorResponse(
        id = nomisCSIPFactorId,
        type = CodeDescription(code = "BUL", description = "Bullying"),
        comment = "Offender causes trouble",
        createDateTime = "2024-04-01T10:00:00",
        createdBy = "JSMITH",
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

  fun stubGetPagedCSIPIds(totalElements: Long = 20, pageSize: Long = 20) {
    // for each page create a response for each csip id starting from 1 up to `totalElements`

    val pages = (totalElements / pageSize) + 1
    (0..pages).forEach { page ->
      val startId = (page * pageSize) + 1
      val endId = min((page * pageSize) + pageSize, totalElements)
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/csip/ids"),
        )
          .withQueryParam("page", equalTo(page.toString()))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(
                csipIdsPagedResponse(
                  totalElements = totalElements,
                  ids = (startId..endId).map { it },
                  pageNumber = page,
                  pageSize = pageSize,
                ),
              ),
          ),
      )
    }
  }

  fun stubMultipleGetCSIP(intProgression: IntProgression) {
    (intProgression).forEach {
      nomisApi.stubFor(
        get(
          urlPathEqualTo("/csip/$it"),
        )
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(nomisCSIPReport(it.toLong())),
          ),
      )
    }
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

  fun stubGetInitialCount(urlPath: String, totalElements: Long, pagedResponse: (totalElements: Long) -> String) = nomisApi.stubGetInitialCount(urlPath, totalElements, pagedResponse)
  fun verifyGetIdsCount(url: String, fromDate: String, toDate: String, prisonId: String? = null) = nomisApi.verifyGetIdsCount(url, fromDate, toDate, prisonId)
  fun verify(countMatchingStrategy: CountMatchingStrategy, requestPatternBuilder: RequestPatternBuilder) = nomisApi.verify(countMatchingStrategy, requestPatternBuilder)
  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder =
    this.withBody(objectMapper.writeValueAsString(body))
}

fun csipIdsPagedResponse(
  totalElements: Long = 10,
  ids: List<Long> = (0L..10L).toList(),
  pageSize: Long = 10,
  pageNumber: Long = 0,
): String {
  val content = ids.map { """{ "csipId": $it }""" }.joinToString { it }
  return pageContent(content, pageSize, pageNumber, totalElements, ids.size)
}
