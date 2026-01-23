package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.PurgeQueueRequest
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import uk.gov.justice.hmpps.sqs.eventTypeMessageAttributes
import java.time.Duration

@Service
class MigrationQueueService(
  private val hmppsQueueService: HmppsQueueService,
  private val telemetryClient: TelemetryClient,
  private val jsonMapper: JsonMapper,
  @Value("\${cancel.queue.purge-frequency-time}") val purgeFrequency: Duration,
  @Value("\${cancel.queue.purge-total-time}") val purgeTotalTime: Duration,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun <T : Enum<T>> sendMessage(message: T, context: MigrationContext<*>, delaySeconds: Int = 0) {
    sendMessage(message, context, delaySeconds, noTracing = false)
  }

  suspend fun <T : Enum<T>> sendMessageNoTracing(message: T, context: MigrationContext<*>, delaySeconds: Int = 0) {
    sendMessage(message, context, delaySeconds, noTracing = true)
  }

  private suspend fun <T : Enum<T>> sendMessage(message: T, context: MigrationContext<*>, delaySeconds: Int = 0, noTracing: Boolean = false) {
    val queue = hmppsQueueService.findByQueueId(context.type.queueId)
      ?: throw IllegalStateException("Queue not found for ${context.type.queueId}")
    queue.sqsClient.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(queue.queueUrl)
        .messageBody(MigrationMessage(message, context).toJson())
        .eventTypeMessageAttributes("prisoner-from-nomis-migration-${context.type.telemetryName}", noTracing = noTracing)
        .delaySeconds(delaySeconds)
        .build(),
    ).await().also {
      telemetryClient.trackEvent(
        message.name,
        mapOf("messageId" to it.messageId(), "migrationId" to context.migrationId),
        null,
      )
    }
  }

  // given counts are approximations there is only a probable chance this returns the correct result
  suspend fun isItProbableThatThereAreStillMessagesToBeProcessed(type: MigrationType): Boolean {
    val queue = hmppsQueueService.findByQueueId(type.queueId)
      ?: throw IllegalStateException("Queue not found for ${type.queueId}")

    return queue.sqsClient.countMessagesOnQueue(queue.queueUrl).await() > 0
  }

  suspend fun countMessagesThatHaveFailed(type: MigrationType): Long {
    val queue = hmppsQueueService.findByQueueId(type.queueId)
      ?: throw IllegalStateException("Queue not found for ${type.queueId}")

    return queue.sqsDlqClient!!.countMessagesOnQueue(queue.dlqUrl!!).await().toLong()
  }

  private fun Any.toJson() = jsonMapper.writeValueAsString(this)

  suspend fun purgeAllMessages(type: MigrationType) {
    val queue = hmppsQueueService.findByQueueId(type.queueId)
      ?: throw IllegalStateException("Queue not found for ${type.queueId}")
    // try purge first, since it is rate limited fall back to less efficient read/delete method
    runCatching {
      hmppsQueueService.purgeQueue(
        PurgeQueueRequest(
          queueName = queue.queueName,
          sqsClient = queue.sqsClient,
          queueUrl = queue.queueUrl,
        ),
      )
    }.onFailure {
      log.debug("Purging queue failed")
      deleteAllMessages(queue)
    }.onSuccess {
      log.debug("Purging queue succeeded")
    }
  }

  private suspend fun deleteAllMessages(queue: HmppsQueue) {
    runCatching {
      val messageCount = queue.sqsClient.countMessagesOnQueue(queue.queueUrl).await()
      log.debug("Purging $messageCount from queue via delete")
      (0..messageCount).forEach { i ->
        queue.sqsClient.receiveMessage(
          ReceiveMessageRequest.builder().queueUrl(queue.queueUrl).maxNumberOfMessages(1).build(),
        ).await().messages().firstOrNull()?.also { msg ->
          queue.sqsClient.deleteMessage(
            DeleteMessageRequest.builder().queueUrl(queue.queueUrl).receiptHandle(msg.receiptHandle()).build(),
          )
        } ?: run {
          log.debug("No more messages found - processed $i out of $messageCount, so giving up reading more messages")
          // when not found any messages since the message count was inaccurate, just break out and finish
          return
        }
      }
    }.onFailure {
      log.warn("Purging queue via delete failed", it)
    }
  }

  suspend fun <T : Enum<T>> purgeAllMessagesNowAndAgainInTheNearFuture(
    migrationContext: MigrationContext<*>,
    message: T,
  ) {
    purgeAllMessages(migrationContext.type)
    // so that MIGRATE_<TYPE>_BY_PAGE messages that are currently generating large numbers of messages
    // have their messages immediately purged, keep purging messages every second for around 10 seconds
    log.debug("Starting to purge ${(purgeTotalTime.toMillis() / purgeFrequency.toMillis()).toInt()} times")
    @Suppress("OPT_IN_USAGE")
    GlobalScope.launch {
      repeat((purgeTotalTime.toMillis() / purgeFrequency.toMillis()).toInt()) {
        delay(purgeFrequency.toMillis())
        purgeAllMessages(migrationContext.type)
      }
      delay(purgeFrequency.toMillis())
      log.debug("Purging finished. Will send cancel shutdown messages")
      sendMessage(
        message,
        migrationContext,
      )
    }
  }
}
