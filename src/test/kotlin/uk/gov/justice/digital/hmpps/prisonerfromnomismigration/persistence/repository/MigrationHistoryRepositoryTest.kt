package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.r2dbc.test.autoconfigure.DataR2dbcTest
import org.springframework.test.context.ActiveProfiles
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.integration.TestBase
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.VISITS
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private const val FILTER = """{
        "prisonIds": [
            "HEI"
        ],
        "visitTypes": [
            "SCON"
        ],
        "fromDateTime": "2022-03-04T16:01:00",
        "ignoreMissingRoom": false
    }"""

@DataR2dbcTest
@ActiveProfiles("test")
class MigrationHistoryRepositoryTest : TestBase() {
  @Autowired
  lateinit var repository: MigrationHistoryRepository

  @BeforeEach
  internal fun setUp() = runBlocking {
    repository.deleteAll()
  }

  @Test
  fun `Can create, get and save a MigrationHistory record`(): Unit = runBlocking {
    repository.save(
      MigrationHistory(
        migrationId = "2022-01-23T10:31:45",
        filter = FILTER,
        migrationType = VISITS,
        estimatedRecordCount = 123_678,
      ),
    )

    val persistedMigrationHistory = repository.findById("2022-01-23T10:31:45") ?: throw AssertionError()
    with(persistedMigrationHistory) {
      assertThat(migrationId).isEqualTo("2022-01-23T10:31:45")
      assertThat(filter).isEqualTo(filter)
      assertThat(migrationType).isEqualTo(VISITS)
      assertThat(status).isEqualTo(MigrationStatus.STARTED)
      assertThat(recordsFailed).isEqualTo(0)
      assertThat(recordsMigrated).isEqualTo(0)
      assertThat(estimatedRecordCount).isEqualTo(123_678)
      assertThat(whenEnded).isNull()
      assertThat(whenStarted).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS))
    }

    persistedMigrationHistory.whenEnded = LocalDateTime.now()
    persistedMigrationHistory.recordsFailed = 2
    persistedMigrationHistory.recordsMigrated = 123_676
    persistedMigrationHistory.status = MigrationStatus.COMPLETED

    val updatedMigrationHistory = repository.save(persistedMigrationHistory)

    with(updatedMigrationHistory) {
      assertThat(migrationId).isEqualTo("2022-01-23T10:31:45")
      assertThat(filter).isEqualTo(filter)
      assertThat(migrationType).isEqualTo(VISITS)
      assertThat(status).isEqualTo(MigrationStatus.COMPLETED)
      assertThat(recordsFailed).isEqualTo(2)
      assertThat(recordsMigrated).isEqualTo(123_676)
      assertThat(estimatedRecordCount).isEqualTo(123_678)
      assertThat(whenEnded).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS))
      assertThat(whenStarted).isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS))
    }
  }
}
