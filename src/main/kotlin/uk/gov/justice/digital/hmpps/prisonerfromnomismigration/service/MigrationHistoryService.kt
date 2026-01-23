package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import kotlinx.coroutines.future.await
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistory
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.persistence.repository.MigrationHistoryRepository
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus.CANCELLED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus.CANCELLED_REQUESTED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationStatus.STARTED
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.LocalDateTime

@Service
class MigrationHistoryService(
  val migrationHistoryRepository: MigrationHistoryRepository,
  private val jsonMapper: JsonMapper,
  private val hmppsQueueService: HmppsQueueService,
  private val generalMappingService: GeneralMappingService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun recordMigrationStarted(
    migrationId: String,
    migrationType: MigrationType,
    estimatedRecordCount: Long = 0,
    filter: Any? = null,
  ) = kotlin.runCatching {
    migrationHistoryRepository.save(
      MigrationHistory(
        migrationId = migrationId,
        migrationType = migrationType,
        estimatedRecordCount = estimatedRecordCount,
        filter = filter?.let { jsonMapper.writeValueAsString(it) },
      ),
    )
  }.onFailure { log.error("Unable to record migration started record", it) }

  suspend fun recordMigrationCompleted(migrationId: String, recordsFailed: Long, recordsMigrated: Long) = runCatching {
    migrationHistoryRepository.findById(migrationId)?.run {
      migrationHistoryRepository.save(
        this.copy(
          whenEnded = LocalDateTime.now(),
          recordsFailed = recordsFailed,
          recordsMigrated = recordsMigrated,
          status = MigrationStatus.COMPLETED,
        ),
      )
    }
  }.onFailure { log.error("Unable to record migration stopped record", it) }

  suspend fun recordMigrationCancelled(migrationId: String, recordsFailed: Long, recordsMigrated: Long) = runCatching {
    migrationHistoryRepository.findById(migrationId)?.run {
      migrationHistoryRepository.save(
        this.copy(
          whenEnded = LocalDateTime.now(),
          recordsFailed = recordsFailed,
          recordsMigrated = recordsMigrated,
          status = CANCELLED,
        ),
      )
    }
  }.onFailure { log.error("Unable to record migration stopped cancelled", it) }

  suspend fun recordMigrationCancelledRequested(migrationId: String) = kotlin.runCatching {
    migrationHistoryRepository.findById(migrationId)?.run {
      migrationHistoryRepository.save(
        this.copy(
          status = CANCELLED_REQUESTED,
        ),
      )
    }
  }.onFailure { log.error("Unable to record migration cancelled requested", it) }

  fun findAll(filter: HistoryFilter) = migrationHistoryRepository.findAllWithFilter(filter)

  suspend fun get(migrationId: String): MigrationHistory = migrationHistoryRepository.findById(migrationId) ?: throw NotFoundException(migrationId)

  suspend fun isCancelling(migrationId: String) = migrationHistoryRepository.findById(migrationId)?.status in listOf(CANCELLED_REQUESTED, CANCELLED)

  suspend fun getActiveMigrationDetails(type: MigrationType): InProgressMigration {
    val queue = hmppsQueueService.findByQueueId(type.queueId)!!

    val toBeProcessedCount = queue.getQueueAttributes()
      .map { it.attributes()[QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES]?.toInt() }.getOrNull()
    val beingProcessedCount = queue.getQueueAttributes()
      .map { it.attributes()[QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE]?.toInt() }.getOrNull()
    val recordsFailedCount =
      queue.getDlqAttributes().map { it.attributes()[QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES]?.toInt() }
        .getOrNull()

    val migrationProperties = migrationHistoryRepository.findFirstByMigrationTypeOrderByWhenStartedDesc(type)
      ?: return InProgressMigration(
        toBeProcessedCount = toBeProcessedCount,
        beingProcessedCount = beingProcessedCount,
        recordsFailed = recordsFailedCount,
      )

    return InProgressMigration(
      recordsMigrated = generalMappingService.getMigrationCount(migrationProperties.migrationId, type),
      toBeProcessedCount = toBeProcessedCount,
      beingProcessedCount = beingProcessedCount,
      recordsFailed = recordsFailedCount,
      whenStarted = migrationProperties.whenStarted,
      migrationId = migrationProperties.migrationId,
      status = migrationProperties.status,
      migrationType = migrationProperties.migrationType,
      estimatedRecordCount = migrationProperties.estimatedRecordCount,
    )
  }

  private suspend fun HmppsQueue.getQueueAttributes() = runCatching {
    this.sqsClient.getQueueAttributes(
      GetQueueAttributesRequest.builder().queueUrl(this.queueUrl).attributeNames(QueueAttributeName.ALL).build(),
    ).await()
  }

  private suspend fun HmppsQueue.getDlqAttributes() = runCatching {
    this.sqsDlqClient!!.getQueueAttributes(
      GetQueueAttributesRequest.builder().queueUrl(this.dlqUrl).attributeNames(QueueAttributeName.ALL).build(),
    ).await()
  }

  suspend fun isMigrationInProgress(type: MigrationType): Boolean {
    val migrationProperties = migrationHistoryRepository.findFirstByMigrationTypeOrderByWhenStartedDesc(type)
    return when (migrationProperties?.status) {
      STARTED, CANCELLED_REQUESTED -> true
      else -> false
    }
  }

  suspend fun updateFilter(migrationId: String, filter: Any) = migrationHistoryRepository.findById(migrationId)
    ?.let { migrationHistoryRepository.save(it.copy(filter = jsonMapper.writeValueAsString(filter))) }
    ?: run { throw NotFoundException(migrationId) }
}

enum class MigrationStatus {
  STARTED,
  COMPLETED,
  CANCELLED_REQUESTED,
  CANCELLED,
}

class NotFoundException(message: String) : RuntimeException(message)
class MigrationAlreadyInProgressException(message: String) : RuntimeException(message)

data class InProgressMigration(
  val recordsMigrated: Long? = null,
  val toBeProcessedCount: Int? = null,
  val beingProcessedCount: Int? = null,
  val recordsFailed: Int? = null,
  val migrationId: String? = null,
  val whenStarted: LocalDateTime? = null,
  val estimatedRecordCount: Long? = null,
  val migrationType: MigrationType? = null,
  var status: MigrationStatus? = null,
)
