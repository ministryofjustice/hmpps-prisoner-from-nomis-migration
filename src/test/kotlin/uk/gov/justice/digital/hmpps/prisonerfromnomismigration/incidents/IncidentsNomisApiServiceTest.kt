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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IncidentIdResponse

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

  @Nested
  @DisplayName("GET /incidents/reconciliation/agencies")
  inner class GetReconciliationAgencyIds {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      incidentsNomisApiMockServer.stubGetIncidentAgencies()

      nomisApiService.getAllAgencies()

      incidentsNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return agencies`() = runTest {
      incidentsNomisApiMockServer.stubGetIncidentAgencies()

      val agencies = nomisApiService.getAllAgencies()

      assertThat(agencies.size).isEqualTo(3)
      assertThat(agencies[0].agencyId).isEqualTo("ASI")
      assertThat(agencies[1].agencyId).isEqualTo("BFI")
      assertThat(agencies[2].agencyId).isEqualTo("WWI")
    }

    @Test
    internal fun `will call the Nomis reconciliation endpoint`() = runTest {
      incidentsNomisApiMockServer.stubGetIncidentAgencies()

      nomisApiService.getAllAgencies()

      incidentsNomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/incidents/reconciliation/agencies")))
    }
  }

  @Nested
  @DisplayName("GET /incidents/reconciliation/agency/{agencyId}/counts")
  inner class GetReconciliationCounts {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      incidentsNomisApiMockServer.stubGetReconciliationAgencyIncidentCounts()

      nomisApiService.getIncidentsReconciliation("ASI")

      incidentsNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS agency id to service`() = runTest {
      incidentsNomisApiMockServer.stubGetReconciliationAgencyIncidentCounts()

      nomisApiService.getIncidentsReconciliation("ASI")

      incidentsNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/incidents/reconciliation/agency/ASI/counts")),
      )
    }

    @Test
    fun `will return agency incident counts`() = runTest {
      incidentsNomisApiMockServer.stubGetReconciliationAgencyIncidentCounts(closed = 4)

      val agencyCount = nomisApiService.getIncidentsReconciliation("ASI")

      assertThat(agencyCount.agencyId).isEqualTo("ASI")
      assertThat(agencyCount.incidentCount.openIncidents).isEqualTo(3)
      assertThat(agencyCount.incidentCount.closedIncidents).isEqualTo(4)
    }
  }

  @Nested
  @DisplayName("GET /incidents/reconciliation/agency/{agencyId}/ids")
  inner class GetReconciliationOpenIncidentIds {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      incidentsNomisApiMockServer.stubGetReconciliationOpenIncidentIds("ASI", 33, 35)

      nomisApiService.getOpenIncidentIds("ASI", 2, 5)

      incidentsNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS agency id to service`() = runTest {
      incidentsNomisApiMockServer.stubGetReconciliationOpenIncidentIds("ASI", 33, 35)

      nomisApiService.getOpenIncidentIds("ASI", 2, 5)

      incidentsNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/incidents/reconciliation/agency/ASI/ids")),
      )
    }

    @Test
    fun `will return open incident Ids`() = runTest {
      incidentsNomisApiMockServer.stubGetReconciliationOpenIncidentIds("ASI", 33, 35)

      val incidentIds = nomisApiService.getOpenIncidentIds("ASI", 2, 5)

      assertThat(incidentIds.totalElements).isEqualTo(40)
      assertThat(incidentIds.content.size).isEqualTo(3)
      assertThat(incidentIds.content).extracting<Long>(IncidentIdResponse::incidentId).containsExactly(33, 34, 35)
    }
  }
}
