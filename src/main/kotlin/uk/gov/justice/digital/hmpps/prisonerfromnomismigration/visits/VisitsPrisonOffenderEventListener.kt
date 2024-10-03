package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
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
class VisitsPrisonOffenderEventListener(
  private val objectMapper: ObjectMapper,
  private val visitSynchronisationService: VisitSynchronisationService,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventvisits", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    val eventType = sqsMessage.MessageAttributes!!.eventType.Value
    return CoroutineScope(Dispatchers.Default).future {
      if (eventFeatureSwitch.isEnabled(eventType)) {
        when (eventType) {
          "VISIT_CANCELLED" -> {
            val (offenderIdDisplay, visitId, auditModuleName) = objectMapper.readValue<VisitCancelledOffenderEvent>(
              sqsMessage.Message,
            )
            log.debug("received VISIT_CANCELLED Offender event for offenderNo $offenderIdDisplay and visitId $visitId with auditModuleName $auditModuleName")
            visitSynchronisationService.cancelVisit(objectMapper.readValue(sqsMessage.Message))
          }

          else -> log.info("Received a message I wasn't expecting {}", eventType)
        }
      } else {
        log.info("Feature switch is disabled for event {}", eventType)
      }
    }.thenAccept { }
  }
}

data class VisitCancelledOffenderEvent(val offenderIdDisplay: String, val visitId: Long, val auditModuleName: String)
