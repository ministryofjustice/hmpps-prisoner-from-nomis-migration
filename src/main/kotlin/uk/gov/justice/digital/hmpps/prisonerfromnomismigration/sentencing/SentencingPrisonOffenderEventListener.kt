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
import java.util.concurrent.CompletableFuture

@Service
class SentencingPrisonOffenderEventListener(
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventsentencing", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "Digital-Prison-Services-prisoner_from_nomis_sentencing_queue", kind = SpanKind.SERVER)
  fun onMessage(message: String): CompletableFuture<Void> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    val eventType = sqsMessage.MessageAttributes.eventType.Value
    return CoroutineScope(Dispatchers.Default).future {
      if (eventFeatureSwitch.isEnabled(eventType)) when (eventType) {
        "SENTENCE_ADJUSTMENT_UPSERTED", "SENTENCE_ADJUSTMENT_DELETED", "KEY_DATE_ADJUSTMENT_UPSERTED", "KEY_DATE_ADJUSTMENT_DELETED" -> {
          log.debug("received $eventType Offender event but right now not doing anything with it")
        }

        else -> log.info("Received a message I wasn't expecting {}", eventType)
      } else {
        log.info("Feature switch is disabled for event {}", eventType)
      }
    }.thenAccept { }
  }
}
