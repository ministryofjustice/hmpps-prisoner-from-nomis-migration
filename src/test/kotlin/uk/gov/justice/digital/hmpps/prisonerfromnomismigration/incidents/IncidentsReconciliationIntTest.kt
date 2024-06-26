package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsApiExtension.Companion.incidentsApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase

class IncidentsReconciliationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var incidentsNomisApi: IncidentsNomisApiMockServer

  @DisplayName("PUT /incidents/reports/reconciliation")
  @Nested
  inner class GenerateIncidentsReconciliationReport {

    @BeforeEach
    fun setUp() {
      incidentsNomisApi.stubGetIncidentAgencies()
      incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts("ASI")
      incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts("BFI")
      incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts("WWI")
      incidentsApi.stubGetIncidents()
    }

    @Test
    fun `will successfully finish report with no errors`() {
      incidentsApi.stubGetIncidentsForAgencies()

      webTestClient.put().uri("/incidents/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      verify(telemetryClient).trackEvent(
        eq("incidents-reports-reconciliation-requested"),
        check { assertThat(it).containsEntry("prisonCount", "3") },
        isNull(),
      )

      awaitReportFinished()
      verify(telemetryClient).trackEvent(
        eq("incidents-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "0")
          assertThat(it).containsEntry("success", "true")
        },
        isNull(),
      )
    }

    @Test
    fun `will output report requested telemetry`() {
      webTestClient.put().uri("/incidents/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      verify(telemetryClient).trackEvent(
        eq("incidents-reports-reconciliation-requested"),
        check { assertThat(it).containsEntry("prisonCount", "3") },
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `will show mismatch counts in report`() {
      incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts(agencyId = "ASI", open = 2)
      incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts(agencyId = "BFI", open = 1, closed = 4)
      incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts(agencyId = "WWI")

      webTestClient.put().uri("/incidents/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("incidents-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "2")
          assertThat(it).containsEntry("success", "true")
          assertThat(it).containsEntry("ASI", "open-dps=3:open-nomis=2; closed-dps=3:closed-nomis=3")
          assertThat(it).containsEntry("BFI", "open-dps=3:open-nomis=1; closed-dps=3:closed-nomis=4")
        },
        isNull(),
      )

      verify(telemetryClient, times(2)).trackEvent(
        eq("incidents-reports-reconciliation-mismatch"),
        any(),
        isNull(),
      )
    }

    @Test
    fun `will complete a report even if some of the checks fail`() {
      incidentsApi.stubGetIncidentsWithError(HttpStatus.INTERNAL_SERVER_ERROR)

      webTestClient.put().uri("/incidents/reports/reconciliation")
        .exchange()
        .expectStatus().isAccepted

      awaitReportFinished()

      verify(telemetryClient, times(3)).trackEvent(
        eq("incidents-reports-reconciliation-mismatch-error"),
        any(),
        isNull(),
      )
    }

    private fun awaitReportFinished() {
      await untilAsserted {
        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-report"),
          any(),
          isNull(),
        )
      }
    }
  }
}
