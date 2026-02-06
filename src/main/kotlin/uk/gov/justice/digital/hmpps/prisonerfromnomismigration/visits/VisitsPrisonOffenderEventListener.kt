package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import java.util.concurrent.CompletableFuture

@Service
class VisitsPrisonOffenderEventListener(
  private val jsonMapper: JsonMapper,
  private val visitSynchronisationService: VisitSynchronisationService,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventvisits", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
    val eventType = sqsMessage.MessageAttributes!!.eventType.Value
    return asCompletableFuture {
      if (eventFeatureSwitch.isEnabled(eventType, "visits")) {
        when (eventType) {
          "VISIT_CANCELLED" -> {
            val (offenderIdDisplay, visitId, auditModuleName) = jsonMapper.readValue<VisitCancelledOffenderEvent>(
              sqsMessage.Message,
            )
            log.debug("received VISIT_CANCELLED Offender event for offenderNo $offenderIdDisplay and visitId $visitId with auditModuleName $auditModuleName")
            visitSynchronisationService.cancelVisit(jsonMapper.readValue(sqsMessage.Message))
          }

          else -> log.info("Received a message I wasn't expecting {}", eventType)
        }
      } else {
        log.info("Feature switch is disabled for event {}", eventType)
      }
    }
  }
}

data class VisitCancelledOffenderEvent(val offenderIdDisplay: String, val visitId: Long, val auditModuleName: String)
