package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

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
        incidentsService.migrateIncident(
          IncidentMigrateRequest(
            nomisIncidentId = NOMIS_INCIDENT_ID,
            description = "Fighting on Prisoner Cell Block H",
          ),
        )
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
          .withRequestBody(matchingJsonPath("nomisIncidentId", equalTo("$NOMIS_INCIDENT_ID")))
          .withRequestBody(matchingJsonPath("description", equalTo("Fighting on Prisoner Cell Block H"))),
      )
    }
  }
}
