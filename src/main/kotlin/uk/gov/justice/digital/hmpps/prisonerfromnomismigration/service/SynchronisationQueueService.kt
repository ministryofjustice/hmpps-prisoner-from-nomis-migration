package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.future.await
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.eventTypeMessageAttributes

const val RETRY_COURT_CASE_SYNCHRONISATION_MAPPING = "court_case_synchronisation_retry"
const val RETRY_COURT_APPEARANCE_SYNCHRONISATION_MAPPING = "court_appearance_synchronisation_retry"
const val RETRY_COURT_CHARGE_SYNCHRONISATION_MAPPING = "court_charge_synchronisation_retry"
const val RETRY_SENTENCE_SYNCHRONISATION_MAPPING = "sentence_synchronisation_retry"
const val RETRY_SENTENCE_TERM_SYNCHRONISATION_MAPPING = "sentence_term_synchronisation_retry"
const val RETRY_PRISONER_MERGE_COURT_CASE_SYNCHRONISATION_MAPPING = "prisoner_merge_court_case_synchronisation_retry"
const val RECALL_BREACH_COURT_EVENT_CHARGE_INSERTED = "recall_breach_court_event_charge_inserted"

@Service
class SynchronisationQueueService(
  private val hmppsQueueService: HmppsQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) {

  suspend fun sendMessage(messageType: String, synchronisationType: SynchronisationType, message: Any, telemetryAttributes: Map<String, String> = mapOf()) {
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
        .eventTypeMessageAttributes("prisoner-from-nomis-synchronisation-$messageType")
        .build(),
    ).await().also {
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
