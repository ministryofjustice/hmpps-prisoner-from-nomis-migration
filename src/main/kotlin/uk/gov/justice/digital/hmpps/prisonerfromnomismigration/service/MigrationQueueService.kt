package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.model.SendMessageRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener.MigrationMessage
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Service
class MigrationQueueService(
  private val hmppsQueueService: HmppsQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,

) {
  private val migrationQueue by lazy { hmppsQueueService.findByQueueId("migration") as HmppsQueue }
  private val migrationSqsClient by lazy { migrationQueue.sqsClient }
  private val migrationQueueUrl by lazy { migrationQueue.queueUrl }
  private val migrationDLQSqsClient by lazy { migrationQueue.sqsDlqClient!! }
  private val migrationDLQUrl by lazy { migrationQueue.dlqUrl!! }

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
}

internal fun AmazonSQS.countMessagesOnQueue(queueUrl: String): Int =
  this.getQueueAttributes(queueUrl, listOf("ApproximateNumberOfMessages"))
    .let { it.attributes["ApproximateNumberOfMessages"]?.toInt() ?: 0 }
