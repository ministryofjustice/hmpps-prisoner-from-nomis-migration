package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

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
class PrisonerRestrictionsDomainEventListener(
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val service: PrisonerRestrictionSynchronisationService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("domaineventprisonerrestrictions", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received domain event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "prisonerrestrictions")) {
            when (eventType) {
              "prison-offender-events.prisoner.merged" -> service.prisonerMerged(sqsMessage.Message.fromJson())
              "prison-offender-events.prisoner.booking.moved" -> service.prisonerBookingMoved(sqsMessage.Message.fromJson())
              "prisoner-offender-search.prisoner.received" -> service.resetPrisonerContactsForAdmission(sqsMessage.Message.fromJson())
              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }

        else -> log.info("Received a non SNS message I wasn't expecting {}", sqsMessage.Type)
      }
    }
  }
  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}
