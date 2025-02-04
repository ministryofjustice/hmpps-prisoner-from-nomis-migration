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

      incidentsNomisApi.stubGetReconciliationOpenIncidentIds("ASI", 33, 35)
      incidentsNomisApi.stubGetReconciliationOpenIncidentIds("BFI", 36, 38)
      incidentsNomisApi.stubGetReconciliationOpenIncidentIds("WWI", 39, 41)
      incidentsNomisApi.stubGetIncident(33)
      incidentsNomisApi.stubGetIncidents(34, 41)
      incidentsApi.stubGetIncidents(33, 41)
    }

    @Nested
    @DisplayName("Happy Path")
    inner class HappyPath {

      @BeforeEach
      fun setUp() {
        incidentsApi.stubGetIncidentCounts()
        incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts("ASI")
        incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts("BFI")
        incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts("WWI")
      }

      @Test
      fun `will successfully finish report with no errors`() {
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
      }

      @Test
      fun `will call incidents api for open and closed counts for each agency`() {
        webTestClient.put().uri("/incidents/reports/reconciliation")
          .exchange()
          .expectStatus().isAccepted

        awaitReportFinished()
        await untilAsserted {
          incidentsApi.verifyGetIncidentCounts(6)
        }
      }

      @Test
      fun `will call incidents api for each open incident details - 3 per agency`() {
        webTestClient.put().uri("/incidents/reports/reconciliation")
          .exchange()
          .expectStatus().isAccepted

        awaitReportFinished()
        await untilAsserted {
          incidentsApi.verifyGetIncidentDetail(9)
        }
      }

      @Test
      fun `will not invoke mismatch telemetry`() {
        incidentsApi.stubGetIncidentsWithError(HttpStatus.INTERNAL_SERVER_ERROR)

        webTestClient.put().uri("/incidents/reports/reconciliation")
          .exchange()
          .expectStatus().isAccepted

        awaitReportFinished()

        verify(telemetryClient, times(0)).trackEvent(
          eq("incidents-reports-reconciliation-mismatch"),
          any(),
          isNull(),
        )

        verify(telemetryClient, times(0)).trackEvent(
          eq("incidents-reports-reconciliation-detail-mismatch"),
          any(),
          isNull(),
        )

        verify(telemetryClient, times(0)).trackEvent(
          eq("incidents-reports-reconciliation-detail-mismatch-error"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("Counts Unhappy Path")
    inner class CountsUnHappyPath {

      @BeforeEach
      fun setUp() {
        incidentsNomisApi.stubGetIncidentAgencies()
        incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts(agencyId = "ASI", open = 2)
        incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts(agencyId = "BFI", open = 1, closed = 4)
        incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts(agencyId = "WWI")
      }

      @Test
      fun `will show mismatch counts in report`() {
        incidentsApi.stubGetIncidentCounts()
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
            assertThat(it).doesNotContainKeys("WWI")
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
      fun `will not invoke detail mismatch`() {
        verify(telemetryClient, times(0)).trackEvent(
          eq("incidents-reports-reconciliation-detail-mismatch"),
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
    }

    @Nested
    @DisplayName("Incident Detail Unhappy Path")
    inner class DetailUnHappyPath {

      @BeforeEach
      fun setUp() {
        incidentsApi.stubGetIncidentCounts()
        incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts(agencyId = "ASI", open = 2)
        incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts(agencyId = "BFI", open = 1, closed = 4)
        incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts(agencyId = "WWI")
      }

      @Test
      fun `will show mismatch counts in report`() {
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
            assertThat(it).doesNotContainKeys("WWI")
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
      fun `will show mismatch differences in report`() {
        incidentsNomisApi.stubGetIncident(33, offenderParty = "Z4321YX", status = "INREQ", type = "ABSCOND", reportedDateTime = "2021-07-08T10:35:18")

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
            assertThat(it).doesNotContainKeys("WWI")
          },
          isNull(),
        )

        verify(telemetryClient, times(2)).trackEvent(
          eq("incidents-reports-reconciliation-mismatch"),
          any(),
          isNull(),
        )

        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-detail-mismatch"),
          check {
            assertThat(it).containsEntry("nomisId", "33")
            assertThat(it).containsKey("dpsId")
            assertThat(it).containsEntry("verdict", "type mismatch")
            assertThat(it).containsEntry("nomis", "IncidentReportDetail(type=ABSCOND, status=INREQ, reportedBy=FSTAFF_GEN, reportedDateTime=2021-07-08T10:35:18, offenderParties=[Z4321YX, Z4321YX], totalStaffParties=1, totalQuestions=1, totalRequirements=1, totalResponses=1)")
            assertThat(it).containsEntry("dps", "IncidentReportDetail(type=ATT_ESC_E, status=AWAN, reportedBy=FSTAFF_GEN, reportedDateTime=2021-07-07T10:35:17, offenderParties=[A1234BC, A1234BC], totalStaffParties=1, totalQuestions=1, totalRequirements=1, totalResponses=1)")
          },
          isNull(),
        )
      }

      @Test
      fun `will show status mismatch differences in report`() {
        incidentsNomisApi.stubGetMismatchIncident()

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
            assertThat(it).doesNotContainKeys("WWI")
          },
          isNull(),
        )

        verify(telemetryClient, times(2)).trackEvent(
          eq("incidents-reports-reconciliation-mismatch"),
          any(),
          isNull(),
        )

        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-detail-mismatch"),
          check {
            assertThat(it).containsEntry("nomisId", "33")
            assertThat(it).containsKey("dpsId")
            assertThat(it).containsEntry("verdict", "Staff parties mismatch")
            assertThat(it).containsEntry("nomis", "IncidentReportDetail(type=ATT_ESC_E, status=INREQ, reportedBy=FSTAFF_GEN, reportedDateTime=2021-07-07T10:35:17, offenderParties=[Z4321YX, Z4321YX], totalStaffParties=0, totalQuestions=1, totalRequirements=0, totalResponses=0)")
            assertThat(it).containsEntry("dps", "IncidentReportDetail(type=ATT_ESC_E, status=AWAN, reportedBy=FSTAFF_GEN, reportedDateTime=2021-07-07T10:35:17, offenderParties=[A1234BC, A1234BC], totalStaffParties=1, totalQuestions=1, totalRequirements=1, totalResponses=1)")
          },
          isNull(),
        )
      }

      @Test
      fun `will show response mismatch differences in report`() {
        incidentsNomisApi.stubGetMismatchResponsesForIncident()

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
            assertThat(it).doesNotContainKeys("WWI")
          },
          isNull(),
        )

        verify(telemetryClient, times(2)).trackEvent(
          eq("incidents-reports-reconciliation-mismatch"),
          any(),
          isNull(),
        )

        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-detail-mismatch"),
          check {
            assertThat(it).containsEntry("nomisId", "33")
            assertThat(it).containsKey("dpsId")
            assertThat(it).containsEntry("verdict", "responses mismatch for question: 1234")
            assertThat(it).containsEntry("nomis", "IncidentReportDetail(type=ATT_ESC_E, status=AWAN, reportedBy=FSTAFF_GEN, reportedDateTime=2021-07-07T10:35:17, offenderParties=[A1234BC, A1234BC], totalStaffParties=1, totalQuestions=1, totalRequirements=1, totalResponses=0)")
            assertThat(it).containsEntry("dps", "IncidentReportDetail(type=ATT_ESC_E, status=AWAN, reportedBy=FSTAFF_GEN, reportedDateTime=2021-07-07T10:35:17, offenderParties=[A1234BC, A1234BC], totalStaffParties=1, totalQuestions=1, totalRequirements=1, totalResponses=1)")
          },
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
    }
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
