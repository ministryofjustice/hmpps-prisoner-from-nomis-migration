package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_RESYNCHRONISATION_MAPPING_BATCH
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_RESYNCHRONISATION_MERGED_MAPPING_BATCH
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING_BATCH
import java.util.concurrent.CompletableFuture

@Service
class AlertsEventListener(
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val alertsSynchronisationService: AlertsSynchronisationService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventalerts", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "syscon-devs-prisoner_from_nomis_alerts_queue", kind = SpanKind.SERVER)
  fun onMessage(message: String): CompletableFuture<Void> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "alerts")) {
            when (eventType) {
              "ALERT-UPDATED" -> alertsSynchronisationService.nomisAlertUpdated(sqsMessage.Message.fromJson())
              "ALERT-INSERTED" -> alertsSynchronisationService.nomisAlertInserted(sqsMessage.Message.fromJson())
              "ALERT-DELETED" -> alertsSynchronisationService.nomisAlertDeleted(sqsMessage.Message.fromJson())
              "prison-offender-events.prisoner.merged" -> alertsSynchronisationService.synchronisePrisonerMerge(sqsMessage.Message.fromJson())
              "prison-offender-events.prisoner.booking.moved" -> alertsSynchronisationService.synchronisePrisonerBookingMoved(sqsMessage.Message.fromJson())
              "prisoner-offender-search.prisoner.received" -> alertsSynchronisationService.resynchronisePrisonerAlertsForAdmission(sqsMessage.Message.fromJson())

              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }

        RETRY_SYNCHRONISATION_MAPPING.name -> alertsSynchronisationService.retryCreateMapping(
          sqsMessage.Message.fromJson(),
        )

        RETRY_SYNCHRONISATION_MAPPING_BATCH.name -> alertsSynchronisationService.retryCreateMappingsBatch(
          sqsMessage.Message.fromJson(),
        )
        RETRY_RESYNCHRONISATION_MAPPING_BATCH.name -> alertsSynchronisationService.retryReplaceMappingsBatch(
          sqsMessage.Message.fromJson(),
        )
        RETRY_RESYNCHRONISATION_MERGED_MAPPING_BATCH.name -> alertsSynchronisationService.retryReplaceMergedMappingsBatch(
          sqsMessage.Message.fromJson(),
        )
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

data class AlertInsertedEvent(
  val bookingId: Long,
  val alertSeq: Long,
  val offenderIdDisplay: String,
)

data class AlertUpdatedEvent(
  val bookingId: Long,
  val alertSeq: Long,
  val offenderIdDisplay: String,
)

private fun asCompletableFuture(
  process: suspend () -> Unit,
): CompletableFuture<Void> = CoroutineScope(Dispatchers.Default).future {
  process()
}.thenAccept { }
