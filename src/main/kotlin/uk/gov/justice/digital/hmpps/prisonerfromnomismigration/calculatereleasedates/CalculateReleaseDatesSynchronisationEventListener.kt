package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CALCULATE_RELEASE_DATES_SYNC_QUEUE_ID
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

@Service
class CalculateReleaseDatesSynchronisationEventListener(
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val synchronisationService: CalculateReleaseDatesSynchronisationService,
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
            "OFFENDER_SENT_CALCULATIONS-INSERTED" -> synchronisationService.sentenceCalculationInserted(sqsMessage.Message.fromJson())
            "OFFENDER_SENT_CALCULATIONS-UPDATED" -> synchronisationService.sentenceCalculationUpdated(sqsMessage.Message.fromJson())
            "OFFENDER_SENT_CALCULATIONS-DELETED" -> synchronisationService.sentenceCalculationDeleted(sqsMessage.Message.fromJson())

            else -> log.info("Received an unexpected calculate release dates event {}", eventType)
          }
        } else {
          log.info("Feature switch is disabled for calculatereleasedates event {}", eventType)
        }
      }

      else -> log.info("Received an unexpected calculate release dates message type {}", sqsMessage.Type)
    }
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

data class SentenceCalculationEvent(
  val eventType: String,
  val eventDatetime: LocalDateTime,
  val offenderIdDisplay: String,
  val bookingId: Long,
  val offenderSentCalculationId: Long,
  override val auditModuleName: String? = null,
) : EventAudited
