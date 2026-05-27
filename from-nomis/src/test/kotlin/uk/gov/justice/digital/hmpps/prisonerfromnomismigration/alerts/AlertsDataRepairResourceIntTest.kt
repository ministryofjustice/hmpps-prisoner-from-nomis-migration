package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiExtension.Companion.dpsAlertsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiMockServer.Companion.resyncedAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.util.*

class AlertsDataRepairResourceIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var alertsNomisApiMockServer: AlertsNomisApiMockServer

  @Autowired
  private lateinit var alertsMappingApiMockServer: AlertsMappingApiMockServer

  @DisplayName("POST /prisoners/{offenderNo}/alerts/resynchronise")
  @Nested
  inner class RepairAlerts {
    val offenderNo = "A1234KT"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/$offenderNo/alerts/resynchronise")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/$offenderNo/alerts/resynchronise")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/$offenderNo/alerts/resynchronise")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      val offenderNo = "A1234KT"
      val bookingId = 1234L
      private val dpsAlertId1 = UUID.fromString("956d4326-b0c3-47ac-ab12-f0165109a6c5")
      private val dpsAlertId2 = UUID.fromString("f612a10f-4827-4022-be96-d882193dfabd")

      @BeforeEach
      fun setUp() {
        alertsNomisApiMockServer.stubGetAlertsToResynchronise(offenderNo, bookingId = bookingId, currentAlertCount = 2)
        dpsAlertsServer.stubResynchroniseAlerts(
          offenderNo = offenderNo,
          response = listOf(
            resyncedAlert().copy(offenderBookId = bookingId, alertSeq = 1, alertUuid = dpsAlertId1),
            resyncedAlert().copy(offenderBookId = bookingId, alertSeq = 2, alertUuid = dpsAlertId2),
          ),
        )
        alertsMappingApiMockServer.stubReplaceMappings(offenderNo)

        webTestClient.post().uri("/prisoners/$offenderNo/alerts/resynchronise")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will retrieve current alerts for the prisoner`() {
        alertsNomisApiMockServer.verify(getRequestedFor(urlPathEqualTo("/prisoners/$offenderNo/alerts/to-migrate")))
      }

      @Test
      fun `will send all alerts to DPS`() {
        dpsAlertsServer.verify(
          postRequestedFor(urlPathEqualTo("/resync/$offenderNo/alerts"))
            .withRequestBodyJsonPath("[0].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("[0].alertSeq", "1")
            .withRequestBodyJsonPath("[1].offenderBookId", "$bookingId")
            .withRequestBodyJsonPath("[1].alertSeq", "2"),
        )
      }

      @Test
      fun `will replaces mapping between the DPS and NOMIS alerts`() {
        alertsMappingApiMockServer.verify(
          putRequestedFor(urlPathEqualTo("/mapping/alerts/$offenderNo/all"))
            .withRequestBodyJsonPath("mappingType", "NOMIS_CREATED")
            .withRequestBodyJsonPath("mappings[0].nomisBookingId", "$bookingId")
            .withRequestBodyJsonPath("mappings[0].nomisAlertSequence", "1")
            .withRequestBodyJsonPath("mappings[0].dpsAlertId", "$dpsAlertId1")
            .withRequestBodyJsonPath("mappings[1].nomisBookingId", "$bookingId")
            .withRequestBodyJsonPath("mappings[1].nomisAlertSequence", "2")
            .withRequestBodyJsonPath("mappings[1].dpsAlertId", "$dpsAlertId2"),
        )
      }

      @Test
      fun `will track telemetry for the resynchronise`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-alerts-resynchronise"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["alertsCount"]).isEqualTo("2")
            assertThat(it["alerts"]).isEqualTo("1, 2")
          },
          isNull(),
        )
      }

      @Test
      fun `will track telemetry for the repair`() {
        verify(telemetryClient).trackEvent(
          eq("from-nomis-synch-alerts-resynchronisation-repair"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
          },
          isNull(),
        )
      }
    }
  }
}
