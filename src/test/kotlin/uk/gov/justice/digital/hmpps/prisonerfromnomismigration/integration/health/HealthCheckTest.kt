package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.health

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonCprApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.CsraApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.FinanceApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property.PropertyApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.StaffDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances.VisitBalanceDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.LocationsApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.MappingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisSyncApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.SentencingApiExtension
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.VisitsApiExtension

@ExtendWith(
  ActivitiesApiExtension::class,
  AlertsDpsApiExtension::class,
  CaseNotesApiExtension::class,
  ContactPersonDpsApiExtension::class,
  CorePersonCprApiExtension::class,
  CourtSchedulerDpsApiExtension::class,
  CourtSentencingDpsApiExtension::class,
  CsraApiExtension::class,
  HmppsAuthApiExtension::class,
  LocationsApiExtension::class,
  MappingApiExtension::class,
  NomisApiExtension::class,
  NomisSyncApiExtension::class,
  OfficialVisitsDpsApiExtension::class,
  OrganisationsDpsApiExtension::class,
  PropertyApiExtension::class,
  SentencingApiExtension::class,
  StaffDpsApiExtension::class,
  TapDpsApiExtension::class,
  VisitBalanceDpsApiExtension::class,
  VisitsApiExtension::class,
)
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
      .jsonPath("components.activitiesApi.status").isEqualTo("UP")
      .jsonPath("components.alertsApi.status").isEqualTo("UP")
      .jsonPath("components.caseNotesApi.status").isEqualTo("UP")
      .jsonPath("components.corePersonApi.status").isEqualTo("UP")
      .jsonPath("components.courtSentencingApi.status").isEqualTo("UP")
      .jsonPath("components.csraApi.status").isEqualTo("UP")
      .jsonPath("components.hmppsAuthApiHealth.status").isEqualTo("UP")
      .jsonPath("components.locationsApi.status").isEqualTo("UP")
      .jsonPath("components.nomisApiHealth.status").isEqualTo("UP")
      .jsonPath("components.nomisMappingApi.status").isEqualTo("UP")
      .jsonPath("components.officialVisitsApi.status").isEqualTo("UP")
      .jsonPath("components.organisationsApi.status").isEqualTo("UP")
      .jsonPath("components.personalRelationshipsApi.status").isEqualTo("UP")
      .jsonPath("components.propertyApi.status").isEqualTo("UP")
      .jsonPath("components.sentencingApi.status").isEqualTo("UP")
      .jsonPath("components.staffApi.status").isEqualTo("UP")
      .jsonPath("components.tapsApi.status").isEqualTo("UP")
      .jsonPath("components.visitBalanceApi.status").isEqualTo("UP")
      .jsonPath("components.visitsApi.status").isEqualTo("UP")
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
    NomisSyncApiExtension.nomisSyncApi.stubHealthPing(status)
    MappingApiExtension.mappingApi.stubHealthPing(status)
    ActivitiesApiExtension.activitiesApi.stubHealthPing(status)
    AlertsDpsApiExtension.dpsAlertsServer.stubHealthPing(status)
    CaseNotesApiExtension.caseNotesApi.stubHealthPing(status)
    CorePersonCprApiExtension.cprCorePersonServer.stubHealthPing(status)
    ContactPersonDpsApiExtension.dpsContactPersonServer.stubHealthPing(status)
    CourtSentencingDpsApiExtension.dpsCourtSentencingServer.stubHealthPing(status)
    CsraApiExtension.csraApi.stubHealthPing(status)
    FinanceApiExtension.financeApi.stubHealthPing(status)
    LocationsApiExtension.locationsApi.stubHealthPing(status)
    OrganisationsDpsApiExtension.dpsOrganisationsServer.stubHealthPing(status)
    PropertyApiExtension.propertyDpsApi.stubHealthPing(status)
    SentencingApiExtension.sentencingApi.stubHealthPing(status)
    StaffDpsApiExtension.dpsStaffServer.stubHealthPing(status)
    TapDpsApiExtension.dpsTapsServer.stubHealthPing(status)
    OfficialVisitsDpsApiExtension.dpsOfficialVisitsServer.stubHealthPing(status)
    VisitBalanceDpsApiExtension.dpsVisitBalanceServer.stubHealthPing(status)
    VisitsApiExtension.visitsApi.stubHealthPing(status)
  }
}
