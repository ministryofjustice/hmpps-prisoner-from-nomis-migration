package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.IncidentsApiExtension.Companion.incidentsApi

private const val NOMIS_INCIDENT_ID = 1234L
private const val INCIDENT_ID = "4321"

@SpringAPIServiceTest
@Import(IncidentsService::class, IncidentsConfiguration::class)
internal class IncidentsServiceTest {

  @Autowired
  private lateinit var incidentsService: IncidentsService

  @Nested
  @DisplayName("POST /incidents/migrate")
  inner class CreateIncidentForMigration {
    @BeforeEach
    internal fun setUp() {
      incidentsApi.stubIncidentForMigration()

      runBlocking {
        incidentsService.migrateIncident(aNomisIncidentResponse())
      }
    }

    @Test
    fun `should call api with OAuth2 token`() {
      incidentsApi.verify(
        postRequestedFor(urlEqualTo("/incidents/migrate"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass data to the api`() {
      incidentsApi.verify(
        postRequestedFor(urlEqualTo("/incidents/migrate"))
          .withRequestBody(matchingJsonPath("incidentId", equalTo("$NOMIS_INCIDENT_ID")))
          .withRequestBody(matchingJsonPath("title", equalTo("There was a fight"))),
      )
    }
  }

  @Nested
  @DisplayName("DELETE /incidents/sync")
  inner class DeleteIncidentForSynchronisation {
    @Nested
    inner class IncidentExists {
      @BeforeEach
      internal fun setUp() {
        incidentsApi.stubIncidentForSyncDelete()
        runBlocking {
          incidentsService.deleteIncident(incidentId = INCIDENT_ID)
        }
      }

      @Test
      fun `should call api with OAuth2 token`() {
        incidentsApi.verify(
          deleteRequestedFor(urlEqualTo("/incidents/sync/$INCIDENT_ID"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }
    }

    @Nested
    inner class IncidentAlreadyDeleted {
      @BeforeEach
      internal fun setUp() {
        incidentsApi.stubIncidentForSyncDeleteNotFound()
      }

      @Test
      fun `should ignore 404 error`() {
        runBlocking {
          incidentsService.deleteIncident(incidentId = INCIDENT_ID)
        }
      }
    }
  }
}
