package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helper.SpringAPIServiceTest

@SpringAPIServiceTest
@Import(IncidentsNomisApiService::class, IncidentsConfiguration::class, IncidentsNomisApiMockServer::class)
class IncidentsNomisApiServiceTest {
  @Autowired
  private lateinit var nomisApiService: IncidentsNomisApiService

  @Autowired
  private lateinit var incidentsNomisApiMockServer: IncidentsNomisApiMockServer

  @Nested
  @DisplayName("GET /incidents/{nomisIncidentId}")
  inner class GetIncident {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      incidentsNomisApiMockServer.stubGetIncident()

      nomisApiService.getIncident(1234L)

      incidentsNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      incidentsNomisApiMockServer.stubGetIncident()

      nomisApiService.getIncident(1234L)

      incidentsNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/incidents/1234")),
      )
    }

    @Test
    fun `will return an incident`() = runTest {
      incidentsNomisApiMockServer.stubGetIncident()

      val incident = nomisApiService.getIncident(1234L)

      assertThat(incident.incidentId).isEqualTo(1234)
      assertThat(incident.status.code).isEqualTo("AWAN")
      assertThat(incident.incidentDateTime).isEqualTo("2017-04-12T16:45:00")
    }

    @Test
    fun `will throw error when incident does not exist`() = runTest {
      incidentsNomisApiMockServer.stubGetIncident(NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        nomisApiService.getIncident(1234L)
      }
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      incidentsNomisApiMockServer.stubGetIncident(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        nomisApiService.getIncident(1234L)
      }
    }
  }

  @Nested
  @DisplayName("GET /incidents/{nomisIncidentId}")
  inner class GetIncidentOrNull {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      incidentsNomisApiMockServer.stubGetIncident()

      nomisApiService.getIncidentOrNull(1234L)

      incidentsNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      incidentsNomisApiMockServer.stubGetIncident()

      nomisApiService.getIncidentOrNull(1234L)

      incidentsNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/incidents/1234")),
      )
    }

    @Test
    fun `will return an incident`() = runTest {
      incidentsNomisApiMockServer.stubGetIncident()

      val incident = nomisApiService.getIncidentOrNull(1234L)!!

      assertThat(incident.incidentId).isEqualTo(1234)
      assertThat(incident.status.code).isEqualTo("AWAN")
      assertThat(incident.incidentDateTime).isEqualTo("2017-04-12T16:45:00")
    }

    @Test
    fun `will not throw error when incident does not exist`() = runTest {
      incidentsNomisApiMockServer.stubGetIncident(NOT_FOUND)

      val incident = nomisApiService.getIncidentOrNull(1234L)
      assertThat(incident).isNull()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      incidentsNomisApiMockServer.stubGetIncident(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        nomisApiService.getIncidentOrNull(1234L)
      }
    }
  }

  @Nested
  @DisplayName("GET /incidents/ids")
  inner class GetIncidentsToMigrate {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      incidentsNomisApiMockServer.stubMultipleGetIncidentIdCounts(1, 20)

      nomisApiService.getIncidentIds(fromDate = null, toDate = null, pageNumber = 0, pageSize = 20)

      incidentsNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will call the Nomis endpoint`() = runTest {
      incidentsNomisApiMockServer.stubMultipleGetIncidentIdCounts(1, 20)

      nomisApiService.getIncidentIds(fromDate = null, toDate = null, pageNumber = 0, pageSize = 20)

      incidentsNomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/incidents/ids")))
    }

    @Test
    fun `will return incidentIds`() = runTest {
      incidentsNomisApiMockServer.stubMultipleGetIncidentIdCounts(3, 10)

      val incidents = nomisApiService.getIncidentIds(fromDate = null, toDate = null, pageNumber = 0, pageSize = 20)

      assertThat(incidents).hasSize(3)
    }
  }
}
