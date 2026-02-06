package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CSRA_SYNC_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class CsraSynchronisationEventListener(
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(CSRA_SYNC_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received csra event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "csra")) {
            when (eventType) {
              "ASSESSMENT-UPDATED" -> null

              else -> log.info("Received a csra message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for csra event {}", eventType)
          }
        }
      }
    }
  }
  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}
