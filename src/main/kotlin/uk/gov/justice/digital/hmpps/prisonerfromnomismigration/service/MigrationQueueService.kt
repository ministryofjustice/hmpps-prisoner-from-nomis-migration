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

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun <T : Enum<T>> sendMessage(message: T, context: MigrationContext<*>, delaySeconds: Int = 0) {
    val queue = hmppsQueueService.findByQueueId(context.type.queueId)
      ?: throw IllegalStateException("Queue not found for ${context.type.queueId}")
    val result =
      queue.sqsClient.sendMessage(
        SendMessageRequest(
          queue.queueUrl,
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
  fun isItProbableThatThereAreStillMessagesToBeProcessed(context: MigrationContext<*>): Boolean {
    val queue = hmppsQueueService.findByQueueId(context.type.queueId)
      ?: throw IllegalStateException("Queue not found for ${context.type.queueId}")

    return queue.sqsClient.countMessagesOnQueue(queue.queueUrl) > 0
  }

  fun countMessagesThatHaveFailed(context: MigrationContext<*>): Long {
    val queue = hmppsQueueService.findByQueueId(context.type.queueId)
      ?: throw IllegalStateException("Queue not found for ${context.type.queueId}")

    return queue.sqsDlqClient!!.countMessagesOnQueue(queue.dlqUrl!!).toLong()
  }

  private fun Any.toJson() = objectMapper.writeValueAsString(this)
  fun purgeAllMessages(context: MigrationContext<*>) {
    val queue = hmppsQueueService.findByQueueId(context.type.queueId)
      ?: throw IllegalStateException("Queue not found for ${context.type.queueId}")
    // try purge first, since it is rate limited fall back to less efficient read/delete method
    kotlin.runCatching {
      hmppsQueueService.purgeQueue(
        PurgeQueueRequest(
          queueName = queue.queueName,
          sqsClient = queue.sqsClient,
          queueUrl = queue.queueUrl
        )
      )
    }.onFailure {
      log.debug("Purging queue failed")
      deleteAllMessages(queue)
    }.onSuccess {
      log.debug("Purging queue succeeded")
    }
  }

  private fun deleteAllMessages(queue: HmppsQueue) {
    kotlin.runCatching {
      val messageCount = queue.sqsClient.countMessagesOnQueue(queue.queueUrl)
      log.debug("Purging $messageCount from queue via delete")
      run repeatBlock@{
        repeat(messageCount) {
          queue.sqsClient.receiveMessage(ReceiveMessageRequest(queue.queueUrl).withMaxNumberOfMessages(1)).messages.firstOrNull()
            ?.also { msg ->
              queue.sqsClient.deleteMessage(DeleteMessageRequest(queue.queueUrl, msg.receiptHandle))
            } ?: run {
            log.debug("No more messages found after $it so giving up reading more messages")
            // when not found any messages since the message count was inaccurate, just break out and finish
            return@repeatBlock
          }
        }
      }
    }.onFailure {
      log.warn("Purging queue via delete failed", it)
    }
  }

  fun <T : Enum<T>> purgeAllMessagesNowAndAgainInTheNearFuture(
    migrationContext: MigrationContext<*>,
    message: T
  ) {
    purgeAllMessages(migrationContext)
    // so that MIGRATE_<TYPE>_BY_PAGE messages that are currently generating large numbers of messages
    // have their messages immediately purged, keep purging messages every second for around 10 seconds
    log.debug("Starting to purge ${(purgeTotalTime.toMillis() / purgeFrequency.toMillis()).toInt()} times")
    GlobalScope.launch {
      repeat((purgeTotalTime.toMillis() / purgeFrequency.toMillis()).toInt()) {
        delay(purgeFrequency.toMillis())
        purgeAllMessages(migrationContext)
      }
      delay(purgeFrequency.toMillis())
      log.debug("Purging finished. Will send cancel shutdown messages")
      sendMessage(
        message,
        migrationContext
      )
    }
  }
}

internal fun AmazonSQS.countMessagesOnQueue(queueUrl: String): Int =
  this.getQueueAttributes(queueUrl, listOf("ApproximateNumberOfMessages"))
    .let { it.attributes["ApproximateNumberOfMessages"]?.toInt() ?: 0 }
