package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.future.await
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.eventTypeMessageAttributes

const val RETRY_COURT_CASE_SYNCHRONISATION_MAPPING = "court_case_synchronisation_retry"
const val RETRY_COURT_APPEARANCE_SYNCHRONISATION_MAPPING = "court_appearance_synchronisation_retry"
const val RETRY_COURT_CHARGE_SYNCHRONISATION_MAPPING = "court_charge_synchronisation_retry"
const val RETRY_SENTENCE_SYNCHRONISATION_MAPPING = "sentence_synchronisation_retry"
const val RETRY_SENTENCE_TERM_SYNCHRONISATION_MAPPING = "sentence_term_synchronisation_retry"
const val RETRY_PRISONER_MERGE_COURT_CASE_SYNCHRONISATION_MAPPING = "prisoner_merge_court_case_synchronisation_retry"
const val RETRY_COURT_CASE_BOOKING_CLONE_SYNCHRONISATION_MAPPING = "court_case_booking_clone_synchronisation_retry"
const val RECALL_BREACH_COURT_EVENT_CHARGE_INSERTED = "recall_breach_court_event_charge_inserted"
const val RECALL_SENTENCE_ADJUSTMENTS_SYNCHRONISATION = "courtsentencing.resync.sentence-adjustments"
const val SENTENCE_RESYNCHRONISATION = "courtsentencing.resync.sentence"
const val CASE_RESYNCHRONISATION = "courtsentencing.resync.case"
const val CASE_BOOKING_RESYNCHRONISATION = "courtsentencing.resync.case.booking"

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
          null,
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
