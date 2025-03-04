package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.health

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonCprApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.LocationsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisSyncApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.SentencingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.VisitsApiExtension

class HealthCheckTest : SqsIntegrationTestBase() {

  @Test
  fun `Health page reports ok`() {
    stubPingWithResponse(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
      .jsonPath("components.personalRelationshipsApi.status").isEqualTo("UP")
      .jsonPath("components.organisationsApi.status").isEqualTo("UP")
      .jsonPath("components.visitsApi.status").isEqualTo("UP")
      .jsonPath("components.sentencingApi.status").isEqualTo("UP")
      .jsonPath("components.nomisMappingApi.status").isEqualTo("UP")
      .jsonPath("components.nomisApiHealth.status").isEqualTo("UP")
      .jsonPath("components.incidentsApi.status").isEqualTo("UP")
      .jsonPath("components.hmppsAuthApiHealth.status").isEqualTo("UP")
      .jsonPath("components.csipApi.status").isEqualTo("UP")
      .jsonPath("components.courtSentencingApi.status").isEqualTo("UP")
      .jsonPath("components.corePersonApi.status").isEqualTo("UP")
      .jsonPath("components.caseNotesApi.status").isEqualTo("UP")
      .jsonPath("components.alertsApi.status").isEqualTo("UP")
      .jsonPath("components.activitiesApi.status").isEqualTo("UP")
  }

  @Test
  fun `Health page reports down`() {
    stubPingWithResponse(404)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus()
      .is5xxServerError
      .expectBody()
      .jsonPath("status").isEqualTo("DOWN")
  }

  @Test
  fun `Health ping page is accessible`() {
    webTestClient.get()
      .uri("/health/ping")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `readiness reports ok`() {
    webTestClient.get()
      .uri("/health/readiness")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `liveness reports ok`() {
    webTestClient.get()
      .uri("/health/liveness")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  private fun stubPingWithResponse(status: Int) {
    HmppsAuthApiExtension.hmppsAuth.stubHealthPing(status)
    NomisApiExtension.nomisApi.stubHealthPing(status)
    VisitsApiExtension.visitsApi.stubHealthPing(status)
    MappingApiExtension.mappingApi.stubHealthPing(status)
    SentencingApiExtension.sentencingApi.stubHealthPing(status)
    ActivitiesApiExtension.activitiesApi.stubHealthPing(status)
    IncidentsApiExtension.incidentsApi.stubHealthPing(status)
    CorePersonCprApiExtension.cprCorePersonServer.stubHealthPing(status)
    CSIPDpsApiExtension.csipDpsApi.stubHealthPing(status)
    LocationsApiExtension.locationsApi.stubHealthPing(status)
    AlertsDpsApiExtension.dpsAlertsServer.stubHealthPing(status)
    CaseNotesApiExtension.caseNotesApi.stubHealthPing(status)
    CourtSentencingDpsApiExtension.dpsCourtSentencingServer.stubHealthPing(status)
    PrisonPersonDpsApiExtension.dpsPrisonPersonApi.stubHealthPing(status)
    ContactPersonDpsApiExtension.dpsContactPersonServer.stubHealthPing(status)
    OrganisationsDpsApiExtension.dpsOrganisationsServer.stubHealthPing(status)
    NomisSyncApiExtension.nomisSyncApi.stubHealthPing(status)
  }
}
