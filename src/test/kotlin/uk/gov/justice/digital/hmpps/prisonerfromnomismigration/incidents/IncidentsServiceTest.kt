package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsApiExtension.Companion.incidentsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisReport
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisStaff
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.NomisSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.PairStringListDescriptionAddendum
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.ReportBasic
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val NOMIS_INCIDENT_ID = 1234L
private const val INCIDENT_ID = "fb4b2e91-91e7-457b-aa17-797f8c5c2f42"

@SpringAPIServiceTest
@Import(IncidentsService::class, IncidentsConfiguration::class)
internal class IncidentsServiceTest {

  @Autowired
  private lateinit var incidentsService: IncidentsService

  @Nested
  @DisplayName("POST /sync/upsert")
  inner class UpsertIncident {
    @BeforeEach
    internal fun setUp() {
      incidentsApi.stubIncidentUpsert()

      runBlocking {
        incidentsService.upsertIncident(aSyncRequest())
      }
    }

    @Test
    fun `should call api with OAuth2 token`() {
      incidentsApi.verify(
        postRequestedFor(urlEqualTo("/sync/upsert"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass data to the api`() {
      incidentsApi.verify(
        postRequestedFor(urlEqualTo("/sync/upsert"))
          .withRequestBody(matchingJsonPath("incidentReport.incidentId", equalTo("$NOMIS_INCIDENT_ID")))
          .withRequestBody(matchingJsonPath("incidentReport.title", equalTo("There was a fight")))
          .withRequestBody(matchingJsonPath("initialMigration", equalTo("true"))),
      )
    }
  }

  @Nested
  @DisplayName("DELETE /incident-reports")
  inner class DeleteIncidentForSynchronisation {
    @Nested
    inner class IncidentExists {
      @BeforeEach
      internal fun setUp() {
        incidentsApi.stubIncidentDelete()
        runBlocking {
          incidentsService.deleteIncident(incidentId = INCIDENT_ID)
        }
      }

      @Test
      fun `should call api with OAuth2 token`() {
        incidentsApi.verify(
          deleteRequestedFor(urlEqualTo("/incident-reports/$INCIDENT_ID"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }
    }

    @Nested
    inner class IncidentAlreadyDeleted {
      @BeforeEach
      internal fun setUp() {
        incidentsApi.stubIncidentDeleteNotFound()
      }

      @Test
      fun `should ignore 404 error`() {
        runBlocking {
          incidentsService.deleteIncident(incidentId = INCIDENT_ID)
        }
      }
    }
  }

  @Nested
  @DisplayName("GET /incident-reports/reference/{nomisIncidentId}")
  inner class GetIncidentByNomisId {
    @BeforeEach
    internal fun setUp() {
      incidentsApi.stubGetBasicIncident()

      runBlocking {
        incidentsService.getIncidentByNomisId(NOMIS_INCIDENT_ID)
      }
    }

    @Test
    fun `should call api with OAuth2 token`() {
      incidentsApi.verify(
        getRequestedFor(urlEqualTo("/incident-reports/reference/$NOMIS_INCIDENT_ID"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will retrieve incident data from the api`() {
      runBlocking {
        val incident = incidentsService.getIncidentByNomisId(NOMIS_INCIDENT_ID)

        with(incident) {
          assertThat(id).isEqualTo(UUID.fromString("fb4b2e91-91e7-457b-aa17-797f8c5c2f42"))
          assertThat(reportReference).isEqualTo("$NOMIS_INCIDENT_ID")
          assertThat(type).isEqualTo(ReportBasic.Type.SELF_HARM_1)
          assertThat(incidentDateAndTime).isEqualTo("2021-07-05T10:35:17")
          assertThat(title).isEqualTo("There was an incident in the exercise yard")
          assertThat(description).isEqualTo("Fred and Jimmy were fighting outside.")
          assertThat(reportedBy).isEqualTo("JSMITH")
          assertThat(reportedAt).isEqualTo("2021-07-05T10:35:17")
          assertThat(status).isEqualTo(ReportBasic.Status.DRAFT)
          assertThat(createdAt).isEqualTo("2021-07-05T10:35:17")
          assertThat(modifiedAt).isEqualTo("2021-07-05T10:35:17")
          assertThat(modifiedBy).isEqualTo("JSMITH")
          assertThat(createdInNomis).isEqualTo(true)
        }
      }
    }
  }
}

fun aSyncRequest() = NomisSyncRequest(
  id = null,
  initialMigration = true,
  incidentReport = NomisReport(
    incidentId = NOMIS_INCIDENT_ID,
    questionnaireId = 543,
    title = "There was a fight",
    description = "On 12/04/2023 approx 16:45 John Smith punched Fred Jones",
    status = NomisStatus(code = "AWAN", description = "Awaiting Analysis"),
    type = "ASSAULT",
    prison = NomisCode(code = "BXI", description = "Brixton"),
    lockedResponse = false,
    incidentDateTime = LocalDateTime.parse("2023-04-12T16:45:00"),
    reportedDateTime = LocalDateTime.parse("2023-04-14T17:55:00"),
    reportingStaff = NomisStaff(username = "BQL16C", staffId = 16288, firstName = "JANE", lastName = "BAKER"),
    history = listOf(),
    offenderParties = listOf(),
    staffParties = listOf(),
    questions = listOf(),
    requirements = listOf(),
    followUpDate = LocalDate.parse("2023-05-16"),
    createdBy = "JSMITH",
    createDateTime = LocalDateTime.parse("2024-07-15T18:35:00"),
    descriptionParts = PairStringListDescriptionAddendum("first", listOf()),
  ),
)
