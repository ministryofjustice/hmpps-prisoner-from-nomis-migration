package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
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
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiExtension.Companion.dpsAlertsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiMockServer.Companion.dpsAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.MigrationResult
import java.time.Duration
import java.util.UUID

class AlertsMigrationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var alertsNomisApiMockServer: AlertsNomisApiMockServer

  @Autowired
  private lateinit var alertsMappingApiMockServer: AlertsMappingApiMockServer

  @Nested
  @DisplayName("POST /migrate/alerts")
  inner class MigrateAlerts {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/alerts")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(AlertsMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/alerts")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(AlertsMigrationFilter())
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/alerts")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(AlertsMigrationFilter())
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      lateinit var migrationResult: MigrationResult

      @Nested
      inner class WhenSomeHaveAlreadyBeenMigrated {
        @BeforeEach
        fun setUp() {
          alertsNomisApiMockServer.stubGetAlertIds(totalElements = 2, pageSize = 10, bookingId = 1234567)
          alertsMappingApiMockServer.stubGetByNomisId(bookingId = 1234567, alertSequence = 1)
          alertsNomisApiMockServer.stubGetAlert(bookingId = 1234567, alertSequence = 2)
          dpsAlertsServer.stubMigrateAlert(response = dpsAlert().copy(alertUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")))
          alertsMappingApiMockServer.stubPostMapping()
          migrationResult = performMigration()
        }

        @Test
        fun `will only migrate one of the alerts`() {
          verify(telemetryClient, times(1)).trackEvent(
            eq("alerts-migration-entity-migrated"),
            any(),
            isNull(),
          )
          verify(telemetryClient).trackEvent(
            eq("alerts-migration-entity-migrated"),
            check {
              assertThat(it).containsEntry("nomisBookingId", "1234567")
              assertThat(it).containsEntry("nomisAlertSequence", "2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WillMigrateAllAlertsNotAlreadyMigrated {
        @BeforeEach
        fun setUp() {
          alertsNomisApiMockServer.stubGetAlertIds(totalElements = 2, pageSize = 10, bookingId = 1234567)
          alertsNomisApiMockServer.stubGetAlert(bookingId = 1234567, alertSequence = 1)
          alertsNomisApiMockServer.stubGetAlert(bookingId = 1234567, alertSequence = 2)
          dpsAlertsServer.stubMigrateAlert(response = dpsAlert().copy(alertUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")))
          dpsAlertsServer.stubMigrateAlert(response = dpsAlert().copy(alertUuid = UUID.fromString("00000000-0000-0000-0000-000000000002")))
          alertsMappingApiMockServer.stubPostMapping()
          migrationResult = performMigration()
        }

        @Test
        fun `will POST all alerts to DPS`() {
          dpsAlertsServer.verify(
            2,
            postRequestedFor(WireMock.urlPathEqualTo("/migrate/alerts")),
          )
        }

        @Test
        fun `will migrate all alerts`() {
          verify(telemetryClient, times(2)).trackEvent(
            eq("alerts-migration-entity-migrated"),
            any(),
            isNull(),
          )
        }
      }
    }
  }

  private fun performMigration(body: AlertsMigrationFilter = AlertsMigrationFilter()): MigrationResult =
    webTestClient.post().uri("/migrate/alerts")
      .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ALERTS")))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(body)
      .exchange()
      .expectStatus().isAccepted.returnResult<MigrationResult>().responseBody.blockFirst()!!
      .also {
        waitUntilCompleted()
      }

  private fun waitUntilCompleted() =
    await atMost Duration.ofSeconds(60) untilAsserted {
      verify(telemetryClient).trackEvent(
        eq("alerts-migration-completed"),
        any(),
        isNull(),
      )
    }
}