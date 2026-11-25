package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CALCULATE_RELEASE_DATES_SYNC_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class CalculateReleaseDatesSynchronisationEventListener(
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(CALCULATE_RELEASE_DATES_SYNC_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> = asCompletableFuture {
    log.debug("Received calculate release dates event message {}", message)
    val sqsMessage = jsonMapper.readValue<SQSMessage>(message)
    when (sqsMessage.Type) {
      "Notification" -> {
        val eventType = sqsMessage.MessageAttributes!!.eventType.Value
        if (eventFeatureSwitch.isEnabled(eventType, "calculatereleasedates")) {
          when (eventType) {
            "OFFENDER_SENT_CALCULATIONS-INSERTED",
            "OFFENDER_SENT_CALCULATIONS-UPDATED",
            "OFFENDER_SENT_CALCULATIONS-DELETED",
            -> log.info("Received calculate release dates event {}: {}", eventType, sqsMessage.Message)

            else -> log.info("Received an unexpected calculate release dates event {}", eventType)
          }
        } else {
          log.info("Feature switch is disabled for calculatereleasedates event {}", eventType)
        }
      }

      else -> log.info("Received an unexpected calculate release dates message type {}", sqsMessage.Type)
    }
  }
}
