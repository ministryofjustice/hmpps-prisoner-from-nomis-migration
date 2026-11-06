package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import java.time.LocalDateTime

private const val BED_ASSIGNMENT_HISTORY_MIGRATION_ID = "e9c7d6a5-43b2-4c10-a987-6b5c4d3e2f10"

class BedAssignmentMigrationHistoryIntTest(
  @Autowired private val migrationHistoryRepository: MigrationHistoryRepository,
) : BedAssignmentIntegrationTestBase() {

  @Nested
  @DisplayName("GET /migrate/history/active/BED_ASSIGNMENT")
  inner class GetActiveMigration {
    @BeforeEach
    internal fun createHistoryRecord() = runTest {
      migrationHistoryRepository.deleteAll()
      migrationHistoryRepository.save(
        MigrationHistory(
          migrationId = BED_ASSIGNMENT_HISTORY_MIGRATION_ID,
          whenStarted = LocalDateTime.now(),
          estimatedRecordCount = 10,
          recordsMigrated = 9,
          migrationType = MigrationType.BED_ASSIGNMENT,
          status = MigrationStatus.STARTED,
        ),
      )
    }

    @AfterEach
    internal fun deleteHistoryRecords() = runBlocking {
      migrationHistoryRepository.deleteAll()
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/migrate/history/active/BED_ASSIGNMENT")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/migrate/history/active/BED_ASSIGNMENT")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/migrate/history/active/BED_ASSIGNMENT")
          .header("Content-Type", "application/json")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @Test
      fun `returns no migration fields when no history exists`() {
        deleteHistoryRecords()
        webTestClient.get().uri("/migrate/history/active/BED_ASSIGNMENT")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
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
      fun `returns active migration and completed prisoner count from local markers`() {
        webTestClient.get().uri("/migrate/history/active/BED_ASSIGNMENT")
          .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_FROM_NOMIS__MIGRATION__RW")))
          .exchange()
          .expectStatus().isOk
          .expectBody()
          .jsonPath("$.migrationId").isEqualTo(BED_ASSIGNMENT_HISTORY_MIGRATION_ID)
          .jsonPath("$.migrationType").isEqualTo("BED_ASSIGNMENT")
          .jsonPath("$.status").isEqualTo("STARTED")
          .jsonPath("$.estimatedRecordCount").isEqualTo(10)
          .jsonPath("$.recordsMigrated").isEqualTo(0) // not implemented
      }
    }
  }
}
