package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.future.await
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.eventTypeMessageAttributes

@Service
class SynchronisationQueueService(
  private val hmppsQueueService: HmppsQueueService,
  private val telemetryClient: TelemetryClient,
  private val jsonMapper: JsonMapper,
) {

  suspend fun sendMessage(
    messageType: String,
    synchronisationType: SynchronisationType,
    message: Any,
    telemetryAttributes: Map<String, String> = mapOf(),
    delaySeconds: Int = 0,
  ) {
    val queue = hmppsQueueService.findByQueueId(synchronisationType.queueId)
      ?: throw IllegalStateException("Queue not found for ${synchronisationType.queueId}")
    val sqsMessage = SQSMessage(
      Type = messageType,
      Message = InternalMessage(message, telemetryAttributes).toJson(),
    )

    val builder = SendMessageRequest.builder()
      .queueUrl(queue.queueUrl)
      .messageBody(sqsMessage.toJson())
      .eventTypeMessageAttributes("prisoner-from-nomis-synchronisation-$messageType")

    if (delaySeconds > 0) {
      builder.delaySeconds(delaySeconds)
    }

    queue.sqsClient.sendMessage(builder.build())
      .await()
      .also {
        telemetryClient.trackEvent(
          messageType,
          mapOf("messageId" to it.messageId()),
        )
      }
      .also {
        val httpStatusCode = it.sdkHttpResponse().statusCode()
        if (httpStatusCode >= 400) {
          throw RuntimeException("Attempt to send message $message resulted in an http $httpStatusCode error")
        }
      }
  }

  private fun Any.toJson() = jsonMapper.writeValueAsString(this)
}

data class InternalMessage<T>(
  val body: T,
  val telemetryAttributes: Map<String, String>,
)
