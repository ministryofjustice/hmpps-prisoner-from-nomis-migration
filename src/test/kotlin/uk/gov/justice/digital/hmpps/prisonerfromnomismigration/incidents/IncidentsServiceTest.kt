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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.model.ReportBasic
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
  inner class CreateIncidentForMigration {
    @BeforeEach
    internal fun setUp() {
      incidentsApi.stubIncidentUpsert()

      runBlocking {
        incidentsService.upsertIncident(aMigrationRequest())
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
  @DisplayName("GET /incident-reports/incident-number/{nomisIncidentId}")
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
        getRequestedFor(urlEqualTo("/incident-reports/incident-number/$NOMIS_INCIDENT_ID"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will retrieve incident data from the api`() {
      runBlocking {
        val incident = incidentsService.getIncidentByNomisId(NOMIS_INCIDENT_ID)

        with(incident) {
          assertThat(id).isEqualTo(UUID.fromString("fb4b2e91-91e7-457b-aa17-797f8c5c2f42"))
          assertThat(incidentNumber).isEqualTo("$NOMIS_INCIDENT_ID")
          assertThat(type).isEqualTo(ReportBasic.Type.SELF_HARM)
          assertThat(incidentDateAndTime).isEqualTo("2021-07-05T10:35:17")
          assertThat(prisonId).isEqualTo("MDI")
          assertThat(title).isEqualTo("There was an incident in the exercise yard")
          assertThat(description).isEqualTo("Fred and Jimmy were fighting outside.")
          assertThat(reportedBy).isEqualTo("JSMITH")
          assertThat(reportedAt).isEqualTo("2021-07-05T10:35:17")
          assertThat(status).isEqualTo(ReportBasic.Status.DRAFT)
          assertThat(assignedTo).isEqualTo("BJONES")
          assertThat(createdAt).isEqualTo("2021-07-05T10:35:17")
          assertThat(modifiedAt).isEqualTo("2021-07-05T10:35:17")
          assertThat(modifiedBy).isEqualTo("JSMITH")
          assertThat(createdInNomis).isEqualTo(true)
        }
      }
    }
  }
}
