package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.DirectionCode
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtMovementRetryMappingMessageTypes.RETRY_MAPPING_COURT_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtMovementRetryMappingMessageTypes.RETRY_MAPPING_COURT_SCHEDULE
import java.util.concurrent.CompletableFuture

@Service
class CourtSchedulerEventListener(
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val courtScheduleService: CourtSchedulerSyncScheduleService,
  private val courtMovementService: CourtSchedulerSyncMovementService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventcourtmovements", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "courtmovements")) {
            @Suppress("UNUSED_EXPRESSION")
            when (eventType) {
              "COURT_EVENTS-INSERTED" -> courtScheduleService.courtScheduleInserted(sqsMessage.Message.fromJson())
              "COURT_EVENTS-UPDATED" -> courtScheduleService.courtScheduleUpdated(sqsMessage.Message.fromJson())
              "COURT_EVENTS-DELETED" -> courtScheduleService.courtScheduleDeleted(sqsMessage.Message.fromJson())
              "EXTERNAL_MOVEMENT-CHANGED" -> courtMovementService.courtMovementChanged(sqsMessage.Message.fromJson())
              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }
        else -> retryMapping(sqsMessage.Type, sqsMessage.Message)
      }
    }
  }

  private suspend fun retryMapping(type: String, message: String) = when (CourtMovementRetryMappingMessageTypes.valueOf(type)) {
    RETRY_MAPPING_COURT_SCHEDULE -> courtScheduleService.retryCreateScheduleMapping(message.fromJson())
    RETRY_MAPPING_COURT_MOVEMENT -> courtMovementService.retryCreateMovementMapping(message.fromJson())
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

data class CourtScheduleEvent(
  val eventId: Long,
  val bookingId: Long,
  val offenderIdDisplay: String,
  val caseId: Long? = null,
  // TODO waiting for direction to be added to events
  val directionCode: DirectionCode? = null,
  override val auditModuleName: String,
) : EventAudited

enum class CourtMovementRetryMappingMessageTypes {
  RETRY_MAPPING_COURT_SCHEDULE,
  RETRY_MAPPING_COURT_MOVEMENT,
}
