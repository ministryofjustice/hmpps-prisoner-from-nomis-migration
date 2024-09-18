package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.atMost
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.AfterEach
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
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.returnResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiExtension.Companion.dpsAlertsServer
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsDpsApiMockServer.Companion.migratedAlert
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.MigrationResult
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.wiremock.withRequestBodyJsonPath
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AlertsByPrisonerMigrationIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var alertsNomisApiMockServer: AlertsNomisApiMockServer

  @Autowired
  private lateinit var alertsMappingApiMockServer: AlertsMappingApiMockServer

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

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
      private lateinit var migrationResult: MigrationResult

      @BeforeEach
      fun setUp() {
        nomisApi.stubGetPrisonIds(totalElements = 2, pageSize = 10, firstOffenderNo = "A0001KT")
        alertsNomisApiMockServer.stubGetAlertsToMigrate(
          offenderNo = "A0001KT",
          currentAlertCount = 1,
          alert = AlertResponse(
            bookingId = 1,
            alertSequence = 1,
            bookingSequence = 10,
            alertCode = CodeDescription("XCU", "Controlled Unlock"),
            type = CodeDescription("X", "Security"),
            date = LocalDate.parse("2021-01-01"),
            expiryDate = LocalDate.parse("2022-01-01"),
            isActive = false,
            isVerified = true,
            comment = "Due to the past",
            authorisedBy = "Security dept",
            audit = NomisAudit(
              createDatetime = "2021-01-01T12:34:56",
              createUsername = "SYS",
              createDisplayName = null,
              modifyDatetime = "2021-02-02T12:24:56",
              modifyUserId = "G.BARNES",
              modifyDisplayName = "GARRY BARNES",
            ),
          ),
        )
        alertsNomisApiMockServer.stubGetAlertsToMigrate(offenderNo = "A0002KT", currentAlertCount = 1)
        dpsAlertsServer.stubMigrateAlerts(offenderNo = "A0001KT", response = listOf(migratedAlert().copy(alertUuid = UUID.fromString("00000000-0000-0000-0000-000000000001"), offenderBookId = 1234567, alertSeq = 1)))
        dpsAlertsServer.stubMigrateAlerts(offenderNo = "A0002KT", response = listOf(migratedAlert().copy(alertUuid = UUID.fromString("00000000-0000-0000-0000-000000000002"), offenderBookId = 1234567, alertSeq = 2)))
        alertsMappingApiMockServer.stubPostMappings("A0001KT")
        alertsMappingApiMockServer.stubPostMappings("A0002KT")
        alertsMappingApiMockServer.stubMigrationCount(recordsMigrated = 2)
        migrationResult = performMigration()
      }

      @Test
      fun `will migrate all prisoner's alerts`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("alerts-migration-entity-migrated"),
          any(),
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("alerts-migration-entity-migrated"),
          check {
            assertThat(it).containsEntry("offenderNo", "A0001KT")
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("alerts-migration-entity-migrated"),
          check {
            assertThat(it).containsEntry("offenderNo", "A0002KT")
          },
          isNull(),
        )
      }

      @Test
      fun `will POST all alerts to DPS for each prisoner`() {
        dpsAlertsServer.verify(postRequestedFor(urlPathEqualTo("/migrate/A0001KT/alerts")))
        dpsAlertsServer.verify(postRequestedFor(urlPathEqualTo("/migrate/A0002KT/alerts")))
      }

      @Test
      fun `will POST mappings for alerts created for each prisoner`() {
        alertsMappingApiMockServer.verify(postRequestedFor(urlPathEqualTo("/mapping/alerts/A0001KT/all")))
        alertsMappingApiMockServer.verify(postRequestedFor(urlPathEqualTo("/mapping/alerts/A0002KT/all")))
      }

      @Test
      fun `will record the number of prisoners migrated`() {
        webTestClient.get().uri("/migrate/alerts/history/${migrationResult.migrationId}")
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ALERTS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationResult.migrationId)
          .jsonPath("$.status").isEqualTo("COMPLETED")
          .jsonPath("$.recordsMigrated").isEqualTo("2")
      }

      @Test
      fun `will transform NOMIS alert to DPS alert`() {
        dpsAlertsServer.verify(
          postRequestedFor(urlPathEqualTo("/migrate/A0001KT/alerts"))
            .withRequestBodyJsonPath("$[0].offenderBookId", "1")
            .withRequestBodyJsonPath("$[0].alertCode", "XCU")
            .withRequestBodyJsonPath("$[0].bookingSeq", "10")
            .withRequestBodyJsonPath("$[0].alertSeq", "1")
            .withRequestBodyJsonPath("$[0].description", "Due to the past")
            .withRequestBodyJsonPath("$[0].authorisedBy", "Security dept")
            .withRequestBodyJsonPath("$[0].activeFrom", "2021-01-01")
            .withRequestBodyJsonPath("$[0].activeTo", "2022-01-01")
            .withRequestBodyJsonPath("$[0].createdAt", "2021-01-01T12:34:56")
            .withRequestBodyJsonPath("$[0].createdBy", "SYS")
            .withRequestBodyJsonPath("$[0].createdByDisplayName", "SYS")
            .withRequestBodyJsonPath("$[0].updatedAt", "2021-02-02T12:24:56")
            .withRequestBodyJsonPath("$[0].updatedBy", "G.BARNES")
            .withRequestBodyJsonPath("$[0].updatedByDisplayName", "GARRY BARNES"),
        )
      }
    }

    @Nested
    inner class ErrorRecovery {
      @BeforeEach
      fun setUp() {
        nomisApi.stubGetPrisonIds(totalElements = 1, pageSize = 10, firstOffenderNo = "A0001KT")
        alertsNomisApiMockServer.stubGetAlertsToMigrate(offenderNo = "A0001KT", currentAlertCount = 1)
        dpsAlertsServer.stubMigrateAlerts(offenderNo = "A0001KT", response = listOf(migratedAlert().copy(alertUuid = UUID.fromString("00000000-0000-0000-0000-000000000001"), offenderBookId = 1234567, alertSeq = 1)))
        alertsMappingApiMockServer.stubPostMappingsFailureFollowedBySuccess(offenderNo = "A0001KT")
        performMigration()
      }

      @Test
      fun `will POST the alerts to DPS only once`() {
        dpsAlertsServer.verify(1, postRequestedFor(urlPathEqualTo("/migrate/A0001KT/alerts")))
      }

      @Test
      fun `will POST mappings for alerts twice due to the single error`() {
        alertsMappingApiMockServer.verify(2, postRequestedFor(urlPathEqualTo("/mapping/alerts/A0001KT/all")))
      }
    }
  }

  @Nested
  @DisplayName("GET /migrate/alerts/history")
  inner class GetAll {
    @BeforeEach
    internal fun createHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.ALERTS,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-02T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-02T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-02T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.ALERTS,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-02T02:00:00",
            whenStarted = LocalDateTime.parse("2020-01-02T02:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-02T03:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.ALERTS,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-03T02:00:00",
            whenStarted = LocalDateTime.parse("2020-01-03T02:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-03T03:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.ALERTS,
          ),
        )
      }
    }

    @AfterEach
    internal fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/migrate/alerts/history")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/alerts/history")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/alerts/history")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `can read all records`() {
      webTestClient.get().uri("/migrate/alerts/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ALERTS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(4)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-03T02:00:00")
        .jsonPath("$[1].migrationId").isEqualTo("2020-01-02T02:00:00")
        .jsonPath("$[2].migrationId").isEqualTo("2020-01-02T00:00:00")
        .jsonPath("$[3].migrationId").isEqualTo("2020-01-01T00:00:00")
    }
  }

  @Nested
  @DisplayName("GET /migrate/alerts/history/{migrationId}")
  inner class Get {
    @BeforeEach
    internal fun createHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.ALERTS,
          ),
        )
      }
    }

    @AfterEach
    internal fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/migrate/alerts/history/2020-01-01T00:00:00")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/alerts/history/2020-01-01T00:00:00")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/alerts/history/2020-01-01T00:00:00")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `can read record`() {
      webTestClient.get().uri("/migrate/alerts/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ALERTS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.status").isEqualTo("COMPLETED")
    }
  }

  @Nested
  @DisplayName("GET /migrate/alerts/active-migration")
  inner class GetActiveMigration {
    @BeforeEach
    internal fun createHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
            status = MigrationStatus.STARTED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.ALERTS,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2019-01-01T00:00:00",
            whenStarted = LocalDateTime.parse("2019-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2019-01-01T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_567,
            recordsFailed = 0,
            migrationType = MigrationType.ALERTS,
          ),
        )
      }
    }

    @AfterEach
    internal fun deleteHistoryRecords() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/migrate/alerts/active-migration")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/alerts/active-migration")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/alerts/active-migration")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `will return dto with null contents if no migrations are found`() {
      deleteHistoryRecords()
      webTestClient.get().uri("/migrate/alerts/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ALERTS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").doesNotExist()
        .jsonPath("$.whenStarted").doesNotExist()
        .jsonPath("$.recordsMigrated").doesNotExist()
        .jsonPath("$.estimatedRecordCount").doesNotExist()
        .jsonPath("$.status").doesNotExist()
        .jsonPath("$.migrationType").doesNotExist()
    }

    @Test
    internal fun `can read active migration data`() {
      alertsMappingApiMockServer.stubSingleItemByMigrationId(migrationId = "2020-01-01T00:00:00", count = 123456)
      webTestClient.get().uri("/migrate/alerts/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ALERTS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.whenStarted").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$.recordsMigrated").isEqualTo(123456)
        .jsonPath("$.toBeProcessedCount").isEqualTo(0)
        .jsonPath("$.beingProcessedCount").isEqualTo(0)
        .jsonPath("$.recordsFailed").isEqualTo(0)
        .jsonPath("$.estimatedRecordCount").isEqualTo(123567)
        .jsonPath("$.status").isEqualTo("STARTED")
        .jsonPath("$.migrationType").isEqualTo("ALERTS")
    }
  }

  @Nested
  @DisplayName("POST /migrate/alerts/{migrationId}/cancel/")
  inner class TerminateMigrationAlerts {
    @BeforeEach
    internal fun setUp() {
      webTestClient.delete().uri("/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATION_ADMIN")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().is2xxSuccessful
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/migrate/alerts/{migrationId}/cancel", "some id")
          .headers(setAuthorisation(roles = listOf()))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/migrate/alerts/{migrationId}/cancel", "some id")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/migrate/alerts/{migrationId}/cancel", "some id")
          .contentType(MediaType.APPLICATION_JSON)
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Test
    internal fun `will return a not found if no running migration found`() {
      webTestClient.post().uri("/migrate/alerts/{migrationId}/cancel", "some id")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ALERTS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    internal fun `will terminate a running migration`() {
      nomisApi.stubGetPrisonIds(totalElements = 2, pageSize = 10, firstOffenderNo = "A0001KT")
      alertsNomisApiMockServer.stubGetAlertsToMigrate(offenderNo = "A0001KT", currentAlertCount = 1)
      alertsNomisApiMockServer.stubGetAlertsToMigrate(offenderNo = "A0002KT", currentAlertCount = 1)

      val migrationId = performMigration().migrationId

      webTestClient.post().uri("/migrate/alerts/{migrationId}/cancel", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ALERTS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isAccepted

      webTestClient.get().uri("/migrate/alerts/history/{migrationId}", migrationId)
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ALERTS")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.migrationId").isEqualTo(migrationId)
        .jsonPath("$.status").isEqualTo("CANCELLED_REQUESTED")

      await atMost Duration.ofSeconds(60) untilAsserted {
        webTestClient.get().uri("/migrate/alerts/history/{migrationId}", migrationId)
          .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_ALERTS")))
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(migrationId)
          .jsonPath("$.status").isEqualTo("CANCELLED")
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
