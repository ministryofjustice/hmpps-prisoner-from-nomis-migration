package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.DeleteMessageRequest
import com.amazonaws.services.sqs.model.ReceiveMessageRequest
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.CANCEL_MIGRATE_VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitMigrationStatusCheck
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.PurgeQueueRequest
import java.time.Duration

@Service
class MigrationQueueService(
  private val hmppsQueueService: HmppsQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
  @Value("\${cancel.queue.purge-frequency-time}") val purgeFrequency: Duration,
  @Value("\${cancel.queue.purge-total-time}") val purgeTotalTime: Duration,
) {
  private val migrationQueue by lazy { hmppsQueueService.findByQueueId("migration") as HmppsQueue }
  private val migrationSqsClient by lazy { migrationQueue.sqsClient }
  private val migrationQueueUrl by lazy { migrationQueue.queueUrl }
  private val migrationDLQSqsClient by lazy { migrationQueue.sqsDlqClient!! }
  private val migrationDLQUrl by lazy { migrationQueue.dlqUrl!! }

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun sendMessage(message: Messages, context: MigrationContext<*>, delaySeconds: Int = 0) {
    val result =
      migrationSqsClient.sendMessage(
        SendMessageRequest(
          migrationQueueUrl,
          MigrationMessage(message, context).toJson()
        ).withDelaySeconds(delaySeconds)
      )

    telemetryClient.trackEvent(
      message.name,
      mapOf("messageId" to result.messageId, "migrationId" to context.migrationId),
      null
    )
  }

  // given counts are approximations there is only a probable chance this returns the correct result
  fun isItProbableThatThereAreStillMessagesToBeProcessed(): Boolean =
    migrationSqsClient.countMessagesOnQueue(migrationQueueUrl) > 0

  fun countMessagesThatHaveFailed(): Long = migrationDLQSqsClient.countMessagesOnQueue(migrationDLQUrl).toLong()

  private fun Any.toJson() = objectMapper.writeValueAsString(this)
  fun purgeAllMessages() {
    log.debug("Purging queue")
    // try purge first, since it is rate limited fall back to less efficient read/delete method
    kotlin.runCatching {
      hmppsQueueService.purgeQueue(
        PurgeQueueRequest(
          queueName = migrationQueue.queueName,
          sqsClient = migrationSqsClient,
          queueUrl = migrationQueueUrl
        )
      )
    }.onFailure {
      log.debug("Purging queue failed")
      deleteAllMessages()
    }.onSuccess {
      log.debug("Purging queue succeeded")
    }
  }

  private fun deleteAllMessages() {
    kotlin.runCatching {
      val messageCount = migrationSqsClient.countMessagesOnQueue(migrationQueueUrl)
      log.debug("Purging $messageCount from queue via delete")
      repeat(messageCount) {
        migrationSqsClient.receiveMessage(ReceiveMessageRequest(migrationQueueUrl).withMaxNumberOfMessages(1)).messages.firstOrNull()
          ?.also { msg ->
            migrationSqsClient.deleteMessage(DeleteMessageRequest(migrationQueueUrl, msg.receiptHandle))
          }
      }
    }.onFailure {
      log.warn("Purging queue via delete failed", it)
    }
  }

  fun purgeAllMessagesNowAndAgainInTheNearFuture(migrationContext: MigrationContext<VisitMigrationStatusCheck>) {
    purgeAllMessages()
    // so that MIGRATE_VISITS_BY_PAGE messages that are currently generating large numbers of messages
    // have their messages immediately purged, keep purging messages every second for around 10 seconds
    GlobalScope.launch {
      repeat((purgeTotalTime.toMillis() / purgeFrequency.toMillis()).toInt()) {
        delay(purgeFrequency.toMillis())
        purgeAllMessages()
      }
      delay(purgeFrequency.toMillis())
      sendMessage(
        CANCEL_MIGRATE_VISITS,
        migrationContext
      )
    }
  }
}

internal fun AmazonSQS.countMessagesOnQueue(queueUrl: String): Int =
  this.getQueueAttributes(queueUrl, listOf("ApproximateNumberOfMessages"))
    .let { it.attributes["ApproximateNumberOfMessages"]?.toInt() ?: 0 }
