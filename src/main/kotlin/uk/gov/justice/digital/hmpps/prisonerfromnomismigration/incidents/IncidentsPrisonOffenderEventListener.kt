package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.INCIDENTS_SYNC_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class IncidentsPrisonOffenderEventListener(
  private val incidentsSynchronisationService: IncidentsSynchronisationService,
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(INCIDENTS_SYNC_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "syscon-devs-prisoner_from_nomis_incidents_queue", kind = SpanKind.SERVER)
  fun onMessage(message: String): CompletableFuture<Void> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType)) {
            when {
              eventType.startsWith("INCIDENT-CHANGED") ->
                // INCIDENT-CHANGED-CASES, INCIDENT-CHANGED-PARTIES, INCIDENT-CHANGED-RESPONSES, INCIDENT-CHANGED-REQUIREMENTS,
                incidentsSynchronisationService.synchroniseIncidentUpdate(sqsMessage.Message.fromJson())

              eventType.startsWith("INCIDENT-DELETED") ->
                // INCIDENT-DELETED-CASES, INCIDENT-DELETED-PARTIES, INCIDENT-DELETED-RESPONSES, INCIDENT-DELETED-REQUIREMENTS,
                incidentsSynchronisationService.synchroniseIncidentDelete(sqsMessage.Message.fromJson())

              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }

        RETRY_SYNCHRONISATION_MAPPING.name -> incidentsSynchronisationService.retryCreateIncidentMapping(
          sqsMessage.Message.fromJson(),
        )
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

// Depending on the type of update/delete there will be additional params
// (e.g. incidentPartySeq for INCIDENT-CHANGED-PARTIES, but we're currently only interested in the whole incident, not just the changed item)
data class IncidentsOffenderEvent(
  val incidentCaseId: Long,
  val auditModuleName: String?,
)

private fun asCompletableFuture(
  process: suspend () -> Unit,
): CompletableFuture<Void> {
  return CoroutineScope(Dispatchers.Default).future {
    process()
  }.thenAccept { }
}
