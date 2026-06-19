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
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

@Service
class CsraSynchronisationEventListener(
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val csraSyncService: CsraSyncService,
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
              "ASSESSMENT-INSERTED" -> csraSyncService.create(sqsMessage.Message.fromJson())
              "ASSESSMENT-UPDATED" -> csraSyncService.update(sqsMessage.Message.fromJson())
              "ASSESSMENT-DELETED" -> csraSyncService.delete(sqsMessage.Message.fromJson())

              "prison-offender-events.prisoner.merged" -> null // csraSynchronisationService.synchronisePrisonerMerged(sqsMessage.Message.fromJson())
              "prison-offender-events.prisoner.booking.moved" -> null // csraSynchronisationService.synchronisePrisonerBookingMoved(sqsMessage.Message.fromJson())

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

data class AssessmentUpdateEvent(
  val eventType: String,
  val eventDatetime: LocalDateTime,
  val offenderIdDisplay: String,
  val bookingId: Long,
  val assessmentSeq: Int,
  val assessmentType: String? = null,
  val evaluationResultCode: String? = null,
  val reviewLevelSupType: String? = null,
  val auditModuleName: String? = null,
)
