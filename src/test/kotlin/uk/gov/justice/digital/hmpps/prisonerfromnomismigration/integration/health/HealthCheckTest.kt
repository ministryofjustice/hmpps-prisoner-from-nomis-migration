package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.health

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.PrisonPersonDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.AdjudicationsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.LocationsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
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
    AdjudicationsApiExtension.adjudicationsApi.stubHealthPing(status)
    IncidentsApiExtension.incidentsApi.stubHealthPing(status)
    CSIPApiExtension.csipDpsApi.stubHealthPing(status)
    LocationsApiExtension.locationsApi.stubHealthPing(status)
    AlertsDpsApiExtension.dpsAlertsServer.stubHealthPing(status)
    CaseNotesApiExtension.caseNotesApi.stubHealthPing(status)
    CourtSentencingDpsApiExtension.dpsCourtSentencingServer.stubHealthPing(status)
    PrisonPersonDpsApiExtension.dpsPrisonPersonServer.stubHealthPing(status)
  }
}
