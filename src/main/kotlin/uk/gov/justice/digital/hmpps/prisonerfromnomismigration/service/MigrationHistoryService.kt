package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus.CANCELLED_REQUESTED
import java.time.LocalDateTime

@Service
class MigrationHistoryService(
  private val migrationHistoryRepository: MigrationHistoryRepository,
  private val objectMapper: ObjectMapper
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun recordMigrationStarted(
    migrationId: String,
    migrationType: MigrationType,
    estimatedRecordCount: Long = 0,
    filter: Any? = null
  ) = kotlin.runCatching {
    migrationHistoryRepository.save(
      MigrationHistory(
        migrationId = migrationId,
        migrationType = migrationType,
        estimatedRecordCount = estimatedRecordCount,
        filter = filter?.let { objectMapper.writeValueAsString(it) }
      )
    )
  }.onFailure { log.error("Unable to record migration started record", it) }

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

  fun recordMigrationCancelled(migrationId: String, recordsFailed: Long, recordsMigrated: Long) = runBlocking {
    kotlin.runCatching {
      migrationHistoryRepository.findById(migrationId)?.run {
        migrationHistoryRepository.save(
          this.copy(
            whenEnded = LocalDateTime.now(),
            recordsFailed = recordsFailed,
            recordsMigrated = recordsMigrated,
            status = MigrationStatus.CANCELLED
          )
        )
      }
    }.onFailure { log.error("Unable to record migration stopped cancelled", it) }
  }

  suspend fun recordMigrationCancelledRequested(migrationId: String) =
    kotlin.runCatching {
      migrationHistoryRepository.findById(migrationId)?.run {
        migrationHistoryRepository.save(
          this.copy(
            status = CANCELLED_REQUESTED
          )
        )
      }
    }.onFailure { log.error("Unable to record migration cancelled requested", it) }

  fun findAll(filter: HistoryFilter) = migrationHistoryRepository.findAllWithFilter(filter)

  suspend fun deleteAll() = migrationHistoryRepository.deleteAll()
  suspend fun get(migrationId: String): MigrationHistory =
    migrationHistoryRepository.findById(migrationId) ?: throw NotFoundException(migrationId)

  fun isCancelling(migrationId: String) =
    runBlocking { migrationHistoryRepository.findById(migrationId)?.status == CANCELLED_REQUESTED }
}

enum class MigrationStatus {
  STARTED,
  COMPLETED,
  CANCELLED_REQUESTED,
  CANCELLED,
}

class NotFoundException(message: String) : RuntimeException(message)
