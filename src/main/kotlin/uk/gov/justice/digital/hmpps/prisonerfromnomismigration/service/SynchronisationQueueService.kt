package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.hmpps.sqs.HmppsQueueService

const val RETRY_COURT_CASE_SYNCHRONISATION_MAPPING = "court_case_synchronisation_retry"
const val RETRY_COURT_APPEARANCE_SYNCHRONISATION_MAPPING = "court_appearance_synchronisation_retry"
const val RETRY_COURT_CHARGE_SYNCHRONISATION_MAPPING = "court_charge_synchronisation_retry"
const val RETRY_SENTENCE_SYNCHRONISATION_MAPPING = "sentence_synchronisation_retry"

const val RETRY_CSIP_FACTOR_SYNCHRONISATION_MAPPING = "csip_factor_synchronisation_retry"

@Service
class SynchronisationQueueService(
  private val hmppsQueueService: HmppsQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) {

  fun sendMessage(messageType: String, synchronisationType: SynchronisationType, message: Any, telemetryAttributes: Map<String, String> = mapOf(), delaySeconds: Int = 0) {
    val queue = hmppsQueueService.findByQueueId(synchronisationType.queueId)
      ?: throw IllegalStateException("Queue not found for ${synchronisationType.queueId}")
    val sqsMessage = SQSMessage(
      Type = messageType,
      Message = InternalMessage(message, telemetryAttributes).toJson(),
    )

    queue.sqsClient.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(queue.queueUrl)
        .messageBody(sqsMessage.toJson())
        .build(),
    ).thenAccept {
      telemetryClient.trackEvent(
        messageType,
        mapOf("messageId" to it.messageId()),
        null,
      )
    }
  }

  private fun Any.toJson() = objectMapper.writeValueAsString(this)
}

data class InternalMessage<T>(
  val body: T,
  val telemetryAttributes: Map<String, String>,
)
