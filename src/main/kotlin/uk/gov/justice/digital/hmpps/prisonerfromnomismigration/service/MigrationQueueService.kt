package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

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

  fun sendMessage(message: Messages, context: MigrationContext<*>) {
    val result =
      migrationSqsClient.sendMessage(SendMessageRequest(migrationQueueUrl, MigrationMessage(message, context).toJson()))

    telemetryClient.trackEvent(message.name, mapOf("messageId" to result.messageId, "batchId" to context.batchId), null)
  }

  private fun Any.toJson() = objectMapper.writeValueAsString(this)
}
