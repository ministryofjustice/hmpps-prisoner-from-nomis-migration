package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import java.time.LocalDateTime

class PrisonPersonMigrationApiIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var migrationHistoryRepository: MigrationHistoryRepository

  @Nested
  @DisplayName("/migrate/prisonperson/history")
  inner class GetAllHistory {
    @BeforeEach
    fun createHistoryRecords() {
      runBlocking {
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
            migrationType = MigrationType.PRISONPERSON,
          ),
        )
        migrationHistoryRepository.save(
          MigrationHistory(
            migrationId = "2020-01-02T00:00:00",
            whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
            whenEnded = LocalDateTime.parse("2020-01-01T01:00:00"),
            status = MigrationStatus.COMPLETED,
            estimatedRecordCount = 123_567,
            filter = "",
            recordsMigrated = 123_560,
            recordsFailed = 7,
            migrationType = MigrationType.PRISONPERSON,
          ),
        )
      }
    }

    @AfterEach
    fun tearDown() {
      runBlocking {
        migrationHistoryRepository.deleteAll()
      }
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.get().uri("/migrate/prisonperson/history")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.get().uri("/migrate/prisonperson/history")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access unauthorised with no auth token`() {
      webTestClient.get().uri("/migrate/prisonperson/history")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `can read all records`() {
      webTestClient.get().uri("/migrate/prisonperson/history")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_PRISONPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.size()").isEqualTo(2)
        .jsonPath("$[0].migrationId").isEqualTo("2020-01-01T00:00:00")
        .jsonPath("$[1].migrationId").isEqualTo("2020-01-02T00:00:00")
    }
  }

  @Nested
  @DisplayName("/migrate/prisonperson/history/{migrationid}")
  inner class GetSingleHistory {
    @BeforeEach
    internal fun createHistoryRecords() = runTest {
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
          migrationType = MigrationType.PRISONPERSON,
        ),
      )
    }

    @AfterEach
    fun tearDown() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.get().uri("/migrate/prisonperson/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.get().uri("/migrate/prisonperson/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access unauthorised with no auth token`() {
      webTestClient.get().uri("/migrate/prisonperson/history/2020-01-01T00:00:00")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    fun ` not found`() {
      webTestClient.get().uri("/migrate/prisonperson/history/UNKNOWN")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_PRISONPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isNotFound
    }

    @Test
    internal fun `can get a migration record`() {
      webTestClient.get().uri("/migrate/prisonperson/history/2020-01-01T00:00:00")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_PRISONPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .consumeWith { println(it) }
        .jsonPath("migrationId").isEqualTo("2020-01-01T00:00:00")
    }
  }

  @Nested
  @DisplayName("/migrate/prisonperson/active-migration")
  inner class GetActiveMigration {
    @BeforeEach
    internal fun createHistoryRecords() = runTest {
      migrationHistoryRepository.save(
        MigrationHistory(
          migrationId = "2020-01-01T00:00:00",
          whenStarted = LocalDateTime.parse("2020-01-01T00:00:00"),
          whenEnded = null,
          status = MigrationStatus.STARTED,
          estimatedRecordCount = 123_567,
          filter = "",
          recordsMigrated = 123_560,
          recordsFailed = 7,
          migrationType = MigrationType.PRISONPERSON,
        ),
      )
    }

    @AfterEach
    fun tearDown() = runTest {
      migrationHistoryRepository.deleteAll()
    }

    @Test
    fun `access forbidden when no role`() {
      webTestClient.get().uri("/migrate/prisonperson/active-migration")
        .headers(setAuthorisation(roles = listOf()))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access forbidden with wrong role`() {
      webTestClient.get().uri("/migrate/prisonperson/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
        .exchange()
        .expectStatus().isForbidden
    }

    @Test
    fun `access unauthorised with no auth token`() {
      webTestClient.get().uri("/migrate/prisonperson/active-migration")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isUnauthorized
    }

    @Test
    internal fun `should get the active migration record`() {
      webTestClient.get().uri("/migrate/prisonperson/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_PRISONPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("migrationId").isEqualTo("2020-01-01T00:00:00")
    }

    @Test
    internal fun `should return nothing if no active migration`() = runTest {
      migrationHistoryRepository.deleteAll()

      webTestClient.get().uri("/migrate/prisonperson/active-migration")
        .headers(setAuthorisation(roles = listOf("ROLE_MIGRATE_PRISONPERSON")))
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("migrationId").doesNotExist()
    }
  }
}
