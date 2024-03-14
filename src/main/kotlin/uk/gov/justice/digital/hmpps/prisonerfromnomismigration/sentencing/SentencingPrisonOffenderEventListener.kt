package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType
import java.util.concurrent.CompletableFuture

@Service
class SentencingPrisonOffenderEventListener(
  private val sentencingAdjustmentsSynchronisationService: SentencingAdjustmentsSynchronisationService,
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventsentencing", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "syscon-devs-prisoner_from_nomis_sentencing_queue", kind = SpanKind.SERVER)
  fun onMessage(message: String): CompletableFuture<Void> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType)) {
            when (eventType) {
              "SENTENCE_ADJUSTMENT_UPSERTED" -> sentencingAdjustmentsSynchronisationService.synchroniseSentenceAdjustmentCreateOrUpdate(
                (sqsMessage.Message.fromJson()),
              )

              "SENTENCE_ADJUSTMENT_DELETED" -> sentencingAdjustmentsSynchronisationService.synchroniseSentenceAdjustmentDelete(
                (sqsMessage.Message.fromJson()),
              )

              "KEY_DATE_ADJUSTMENT_UPSERTED" -> sentencingAdjustmentsSynchronisationService.synchroniseKeyDateAdjustmentCreateOrUpdate(
                (sqsMessage.Message.fromJson()),
              )

              "KEY_DATE_ADJUSTMENT_DELETED" -> sentencingAdjustmentsSynchronisationService.synchroniseKeyDateAdjustmentDelete(
                (sqsMessage.Message.fromJson()),
              )

              "BOOKING_NUMBER-CHANGED" -> sentencingAdjustmentsSynchronisationService.synchronisePrisonerMerge(
                (sqsMessage.Message.fromJson()),
              )

              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }

        SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING.name -> sentencingAdjustmentsSynchronisationService.retryCreateSentenceAdjustmentMapping(
          sqsMessage.Message.fromJson(),
        )
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

data class SentenceAdjustmentOffenderEvent(
  val offenderIdDisplay: String,
  val bookingId: Long,
  val sentenceSeq: Long,
  val adjustmentId: Long,
  val auditModuleName: String?,
)

data class KeyDateAdjustmentOffenderEvent(
  val offenderIdDisplay: String,
  val bookingId: Long,
  val adjustmentId: Long,
  val auditModuleName: String?,
)

data class PrisonerMergeEvent(
  val bookingId: Long,
)
private fun asCompletableFuture(
  process: suspend () -> Unit,
): CompletableFuture<Void> {
  return CoroutineScope(Dispatchers.Default).future {
    process()
  }.thenAccept { }
}
