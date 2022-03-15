package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import java.time.LocalDateTime

@Service
class MigrationHistoryService(
  private val migrationHistoryRepository: MigrationHistoryRepository,
  private val objectMapper: ObjectMapper
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun recordMigrationStarted(
    migrationId: String,
    migrationType: MigrationType,
    estimatedRecordCount: Long = 0,
    filter: Any? = null
  ) = runBlocking {
    kotlin.runCatching {
      migrationHistoryRepository.save(
        MigrationHistory(
          migrationId = migrationId,
          migrationType = migrationType,
          estimatedRecordCount = estimatedRecordCount,
          filter = filter?.let { objectMapper.writeValueAsString(it) }
        )
      )
    }.onFailure { log.error("Unable to record migration started record", it) }
  }

  fun recordMigrationCompleted(migrationId: String, recordsFailed: Long, recordsMigrated: Long) = runBlocking {
    kotlin.runCatching {
      migrationHistoryRepository.findById(migrationId)?.run {
        migrationHistoryRepository.save(
          this.copy(
            whenEnded = LocalDateTime.now(),
            recordsFailed = recordsFailed,
            recordsMigrated = recordsMigrated,
            status = MigrationStatus.COMPLETED
          )
        )
      }
    }.onFailure { log.error("Unable to record migration stopped record", it) }
  }

  suspend fun findAll(filter: HistoryFilter) = migrationHistoryRepository.findWithCriteria(
    fromDateTime = filter.fromDateTime,
    toDateTime = filter.toDateTime,
    includeOnlyFailures = filter.includeOnlyFailures,
    filterContains = filter.filterContains,
    // this is nasty, can't work out how to deal with an empty list in the SQL so either pass our list on ALL migration types
    // there has to be a better way, but just can't work it out.
    migrationTypes = if (filter.migrationTypes.isNullOrEmpty()) MigrationType.values()
      .map { it.name } else filter.migrationTypes,
  )

  suspend fun deleteAll() = migrationHistoryRepository.deleteAll()
}

enum class MigrationType {
  VISITS,
}

enum class MigrationStatus {
  STARTED,
  COMPLETED,
}
