package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IncidentAgencyId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IncidentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IncidentStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IncidentsCount
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IncidentsReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Offender
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.OffenderParty
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.Staff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.pageContent
import java.lang.Long.min
import java.time.LocalDate

@Component
class IncidentsNomisApiMockServer(private val objectMapper: ObjectMapper) {
  companion object {
    const val INCIDENTS_ID_URL = "/incidents/ids"
  }
  fun stubHealthPing(status: Int) {
    nomisApi.stubFor(
      get("/health/ping").willReturn(
        aResponse().withHeader("Content-Type", "application/json").withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubMultipleGetIncidentIdCounts(totalElements: Long, pageSize: Long) {
    // for each page create a response for each incident id starting from 1 up to `totalElements`

    val pages = (totalElements / pageSize) + 1
    (0..pages).forEach { page ->
      val startIncidentId = (page * pageSize) + 1
      val endIncidentId = min((page * pageSize) + pageSize, totalElements)
      nomisApi.stubFor(
        get(urlPathEqualTo("/incidents/ids"))
          .withQueryParam("page", equalTo(page.toString()))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(
                incidentIdsPagedResponse(
                  totalElements = totalElements,
                  ids = (startIncidentId..endIncidentId).map { it },
                  pageNumber = page,
                  pageSize = pageSize,
                ),
              ),
          ),
      )
    }
  }

  fun stubGetIncident(
    nomisIncidentId: Long = 1234,
    lastModifiedDateTime: String = "2021-07-23T10:35:17",
    offenderParty: String = "A1234BC",
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/incidents/$nomisIncidentId"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(incidentResponse(nomisIncidentId, lastModifiedDateTime, offenderParty)),
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
  fun stubGetIncidents(startIncidentId: Long, endIncidentId: Long) {
    (startIncidentId..endIncidentId).forEach { nomisIncidentId ->
      stubGetIncident(nomisIncidentId)
    }
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

  fun stubMultipleGetIncidents(intProgression: IntProgression) {
    (intProgression).forEach {
      nomisApi.stubFor(
        get(urlPathEqualTo("/incidents/$it"))
          .willReturn(
            aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
              .withBody(incidentResponse(nomisIncidentId = it.toLong())),
          ),
      )
    }
  }

  fun stubGetIncidentAgencies() {
    nomisApi.stubFor(
      get(urlPathEqualTo("/incidents/reconciliation/agencies"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(incidentAgencies()),
        ),
    )
  }

  fun stubGetIncidentAgenciesWithError(status: HttpStatus) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/incidents/reconciliation/agencies"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody("""{"message":"Error"}"""),
        ),
    )
  }

  fun stubGetReconciliationAgencyIncidentCounts(agencyId: String = "ASI", open: Long = 3, closed: Long = 3) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/incidents/reconciliation/agency/$agencyId/counts"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(incidentAgencyCount(agencyId, open, closed)),
        ),
    )
  }
  fun stubGetIncidentForAgencyReconciliationWithError(agencyId: String = "ASI", status: HttpStatus) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/incidents/reconciliation/agency/$agencyId/counts"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(status.value())
            .withBody("""{"message":"Error"}"""),
        ),
    )
  }

  fun stubGetReconciliationOpenIncidentIds(agencyId: String, start: Int = 33, finish: Long = 35) {
    nomisApi.stubFor(
      get(urlPathMatching("/incidents/reconciliation/agency/$agencyId/ids"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(
              incidentIdsPagedResponse(
                totalElements = 40,
                ids = (start..finish).map { it },
                pageNumber = 2,
                pageSize = 3,
              ),
            ),
        ),
    )
  }

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder =
    this.withBody(objectMapper.writeValueAsString(body))

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
  fun verify(countMatchingStrategy: CountMatchingStrategy, requestPatternBuilder: RequestPatternBuilder) = nomisApi.verify(countMatchingStrategy, requestPatternBuilder)
}

fun incidentIdsPagedResponse(
  totalElements: Long = 10,
  ids: List<Long> = (0L..10L).toList(),
  pageSize: Long = 10,
  pageNumber: Long = 0,
): String {
  val content = ids.map { """{ "incidentId": $it }""" }.joinToString { it }
  return pageContent(content, pageSize, pageNumber, totalElements, ids.size)
}

private fun incidentAgencies() =
  listOf(IncidentAgencyId("ASI"), IncidentAgencyId("BFI"), IncidentAgencyId("WWI"))

private fun incidentAgencyCount(agencyId: String, open: Long, closed: Long) =
  IncidentsReconciliationResponse(agencyId = agencyId, IncidentsCount(openIncidents = open, closedIncidents = closed))

private fun incidentResponse(
  nomisIncidentId: Long = 1234,
  lastModifiedDateTime: String = "2021-07-23T10:35:17",
  offenderParty: String = "A1234BC",
): IncidentResponse =
  IncidentResponse(
    incidentId = nomisIncidentId,
    questionnaireId = 45456,
    title = "This is a test incident",
    description = "On 12/04/2023 approx 16:45 Mr Smith tried to escape.",
    status = IncidentStatus(
      code = "AWAN",
      description = "Awaiting Analysis",
      listSequence = 1,
      standardUser = true,
      enhancedUser = true,
    ),
    agency = CodeDescription(
      code = "BXI",
      description = "Brixton",
    ),
    type = "ATT_ESC_E",
    lockedResponse = false,
    incidentDateTime = "2017-04-12T16:45:00",
    reportingStaff = Staff(
      username = "FSTAFF_GEN",
      staffId = 485572,
      firstName = "FRED",
      lastName = "STAFF",
    ),
    followUpDate = LocalDate.parse("2017-04-12"),
    createDateTime = "2021-02-06T12:36:00",
    createdBy = "JIM SMITH",
    lastModifiedBy = "JIM_ADM",
    lastModifiedDateTime = lastModifiedDateTime,
    reportedDateTime = "2024-02-06T12:36:00",
    staffParties = listOf(),
    offenderParties = listOf(
      OffenderParty(
        offender =
        Offender(offenderParty, firstName = "Fred", lastName = "smith"),
        role = CodeDescription("ABS", "Absconder"),
        createDateTime = "2024-02-06T12:36:00",
        createdBy = "JIM",
      ),
    ),
    requirements = listOf(),
    questions = listOf(),
    history = listOf(),
  )
