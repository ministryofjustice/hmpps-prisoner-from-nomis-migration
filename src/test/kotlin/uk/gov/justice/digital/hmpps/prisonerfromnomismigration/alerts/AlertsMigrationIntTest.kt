package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration
import java.time.LocalDate
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
          alertsNomisApiMockServer.stubGetAlertIds(totalElements = 2, pageSize = 10, bookingId = 1234567, offenderNo = "A1234KT")
          alertsNomisApiMockServer.stubGetAlert(
            bookingId = 1234567,
            alertSequence = 1,
            alert = AlertResponse(
              bookingId = 1234567,
              alertSequence = 1,
              alertCode = CodeDescription("XR", "Racist"),
              type = CodeDescription("X", "Security"),
              date = LocalDate.parse("2021-01-01"),
              isActive = true,
              isVerified = false,
              audit = NomisAudit(
                createDatetime = "2021-01-01T12:34:56",
                createUsername = "SYS",
                createDisplayName = null,
              ),
            ),
          )
          alertsNomisApiMockServer.stubGetAlert(
            bookingId = 1234567,
            alertSequence = 2,
            alert = AlertResponse(
              bookingId = 1234567,
              alertSequence = 2,
              comment = "This is a comment",
              alertCode = CodeDescription("XEL", "Escape List"),
              type = CodeDescription("X", "Security"),
              date = LocalDate.parse("2021-01-01"),
              isActive = false,
              isVerified = true,
              expiryDate = LocalDate.parse("2022-02-02"),
              authorisedBy = "Security team",
              audit = NomisAudit(
                createDatetime = "2021-01-01T12:34:56",
                createUsername = "A_MARKE",
                createDisplayName = "ANDY MARKE",
                modifyDatetime = "2021-02-02T12:24:56",
                modifyUserId = "P_SNICKET",
                modifyDisplayName = "PAULA SNICKET",
              ),
            ),
          )
          dpsAlertsServer.stubMigrateAlert(response = dpsAlert().copy(alertUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")))
          dpsAlertsServer.stubMigrateAlert(response = dpsAlert().copy(alertUuid = UUID.fromString("00000000-0000-0000-0000-000000000002")))
          alertsMappingApiMockServer.stubPostMapping()
          migrationResult = performMigration()
        }

        @Test
        fun `will POST all alerts to DPS`() {
          dpsAlertsServer.verify(
            2,
            postRequestedFor(urlPathEqualTo("/migrate/alerts")),
          )
        }

        @Test
        fun `will transform NOMIS alert to DPS alert`() {
          dpsAlertsServer.verify(
            postRequestedFor(urlPathEqualTo("/migrate/alerts"))
              .withRequestBodyJsonPath("prisonNumber", "A1234KT")
              .withRequestBodyJsonPath("alertCode", "XR")
              .withRequestBodyJsonPath("description", absent())
              .withRequestBodyJsonPath("authorisedBy", absent())
              .withRequestBodyJsonPath("activeFrom", "2021-01-01")
              .withRequestBodyJsonPath("activeTo", absent())
              .withRequestBodyJsonPath("createdAt", "2021-01-01T12:34:56")
              .withRequestBodyJsonPath("createdBy", "SYS")
              .withRequestBodyJsonPath("createdByDisplayName", "SYS")
              .withRequestBodyJsonPath("updatedAt", absent())
              .withRequestBodyJsonPath("updatedBy", absent())
              .withRequestBodyJsonPath("updatedByDisplayName", absent())
              .withRequestBodyJsonPath("comments.size()", equalTo("0")),
          )
          dpsAlertsServer.verify(
            postRequestedFor(urlPathEqualTo("/migrate/alerts"))
              .withRequestBodyJsonPath("prisonNumber", "A1234KT")
              .withRequestBodyJsonPath("alertCode", "XEL")
              .withRequestBodyJsonPath("description", "This is a comment")
              .withRequestBodyJsonPath("authorisedBy", "Security team")
              .withRequestBodyJsonPath("activeFrom", "2021-01-01")
              .withRequestBodyJsonPath("activeTo", "2022-02-02")
              .withRequestBodyJsonPath("createdAt", "2021-01-01T12:34:56")
              .withRequestBodyJsonPath("createdBy", "A_MARKE")
              .withRequestBodyJsonPath("createdByDisplayName", "ANDY MARKE")
              .withRequestBodyJsonPath("updatedAt", "2021-02-02T12:24:56")
              .withRequestBodyJsonPath("updatedBy", "P_SNICKET")
              .withRequestBodyJsonPath("updatedByDisplayName", "PAULA SNICKET")
              .withRequestBodyJsonPath("comments.size()", equalTo("0")),
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
