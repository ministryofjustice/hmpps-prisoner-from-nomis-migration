package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsApiExtension.Companion.incidentsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.util.UUID

class IncidentDataRepairResourceIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var incidentsNomisApiMockServer: IncidentsNomisApiMockServer

  @Autowired
  private lateinit var incidentsMappingApiMockServer: IncidentsMappingApiMockServer

  @DisplayName("POST /incidents/{incidentId}/repair")
  @Nested
  inner class RepairIncident {
    val nomisIncidentId = 1234L

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/incidents/$nomisIncidentId/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/incidents/$nomisIncidentId/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/incidents/$nomisIncidentId/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      val offenderNo = "A1234KT"
      val bookingId = 1234L
      private val dpsIncidentId = UUID.randomUUID().toString()

      @BeforeEach
      fun setUp() {
        incidentsNomisApiMockServer.stubGetIncident(nomisIncidentId = nomisIncidentId)
        incidentsApi.stubIncidentUpsert(dpsIncidentId = dpsIncidentId)
      }

      @Nested
      inner class IncidentCreateRepair {
        @BeforeEach
        fun setUp() {
          incidentsMappingApiMockServer.stubGetAnyIncidentNotFound()
          webTestClient.post().uri("/incidents/$nomisIncidentId/repair?createIncident=true")
            .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_INCIDENT_REPORTS")))
            .exchange()
            .expectStatus().isOk
        }

        @Test
        fun `will retrieve the incident details from Nomis`() {
          incidentsNomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/incidents/$nomisIncidentId")))
        }

        @Test
        fun `will send the incident to DPS`() {
          incidentsApi.verify(
            postRequestedFor(urlPathEqualTo("/sync/upsert"))
              .withRequestBodyJsonPath("incidentReport.incidentId", equalTo("$nomisIncidentId"))
              .withRequestBodyJsonPath("incidentReport.title", equalTo("This is a test incident")),
          )
        }

        @Test
        fun `will save mapping details`() {
          incidentsMappingApiMockServer.verify(postRequestedFor(urlPathEqualTo("/mapping/incidents")))
        }

        @Test
        fun `will track telemetry for the resynchronise`() {
          verify(telemetryClient).trackEvent(
            eq("incidents-synchronisation-created-success"),
            check {
              assertThat(it["nomisIncidentId"]).isEqualTo("$nomisIncidentId")
              assertThat(it["dpsIncidentId"]).isEqualTo(dpsIncidentId)
            },
            isNull(),
          )
        }

        @Test
        fun `will track telemetry for the repair`() {
          verify(telemetryClient).trackEvent(
            eq("incidents-resynchronisation-repair"),
            check {
              assertThat(it["nomisIncidentId"]).isEqualTo("$nomisIncidentId")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class IncidentUpdateRepair {
        @BeforeEach
        fun setUp() {
          incidentsMappingApiMockServer.stubGetIncident(nomisIncidentId, dpsIncidentId = dpsIncidentId)
          webTestClient.post().uri("/incidents/$nomisIncidentId/repair")
            .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_INCIDENT_REPORTS")))
            .exchange()
            .expectStatus().isOk
        }

        @Test
        fun `will retrieve the incident details from Nomis`() {
          incidentsNomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/incidents/$nomisIncidentId")))
        }

        @Test
        fun `will send the incident to DPS`() {
          incidentsApi.verify(
            postRequestedFor(urlPathEqualTo("/sync/upsert"))
              .withRequestBodyJsonPath("incidentReport.incidentId", equalTo("$nomisIncidentId"))
              .withRequestBodyJsonPath("incidentReport.title", equalTo("This is a test incident")),
          )
        }

        @Test
        fun `will request mapping details`() {
          incidentsMappingApiMockServer.verify(getRequestedFor(urlPathEqualTo("/mapping/incidents/nomis-incident-id/$nomisIncidentId")))
        }

        @Test
        fun `will track telemetry for the resynchronise`() {
          verify(telemetryClient).trackEvent(
            eq("incidents-synchronisation-updated-success"),
            check {
              assertThat(it["nomisIncidentId"]).isEqualTo("$nomisIncidentId")
              assertThat(it["dpsIncidentId"]).isEqualTo(dpsIncidentId)
            },
            isNull(),
          )
        }

        @Test
        fun `will track telemetry for the repair`() {
          verify(telemetryClient).trackEvent(
            eq("incidents-resynchronisation-repair"),
            check {
              assertThat(it["nomisIncidentId"]).isEqualTo("$nomisIncidentId")
            },
            isNull(),
          )
        }
      }
    }
  }
}
