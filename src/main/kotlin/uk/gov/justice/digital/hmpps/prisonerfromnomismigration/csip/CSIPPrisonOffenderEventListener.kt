package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CSIP_SYNC_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class CSIPPrisonOffenderEventListener(
  private val csipSynchronisationService: CSIPSynchronisationService,
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(CSIP_SYNC_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "syscon-devs-prisoner_from_nomis_csip_queue", kind = SpanKind.SERVER)
  fun onMessage(message: String): CompletableFuture<Void> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType)) {
            when (eventType) {
              "CSIP-INSERTED" -> csipSynchronisationService.synchroniseCSIPInsert(sqsMessage.Message.fromJson())
              "CSIP-UPDATED" -> log.debug("Update CSIP")
              "CSIP-DELETED" -> csipSynchronisationService.synchroniseCSIPDelete(sqsMessage.Message.fromJson())
              // TODO check if needed
              // "prison-offender-events.prisoner.merged"

              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }

        RETRY_SYNCHRONISATION_MAPPING.name -> csipSynchronisationService.retryCreateCSIPMapping(
          sqsMessage.Message.fromJson(),
        )
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

// Depending on the type of update/delete there will be additional params - but we don't care
data class CSIPOffenderEvent(
  // We don't know what this is called yet
  val nomisCSIPId: Long,
  val auditModuleName: String?,
)

private fun asCompletableFuture(
  process: suspend () -> Unit,
): CompletableFuture<Void> {
  return CoroutineScope(Dispatchers.Default).future {
    process()
  }.thenAccept { }
}