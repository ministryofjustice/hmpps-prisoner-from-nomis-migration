package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementRetryMappingMessageTypes.RETRY_MAPPING_TEMPORARY_ABSENCE_APPLICATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementRetryMappingMessageTypes.RETRY_MAPPING_TEMPORARY_ABSENCE_OUTSIDE_MOVEMENT
import java.util.concurrent.CompletableFuture

@Service
class ExternalMovementsEventListener(
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val syncService: ExternalMovementsSyncService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventexternalmovements", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "externalmovements")) {
            @Suppress("UNUSED_EXPRESSION")
            when (eventType) {
              "MOVEMENT_APPLICATION-INSERTED" -> syncService.movementApplicationInserted(sqsMessage.Message.fromJson())
              "MOVEMENT_APPLICATION-UPDATED" -> syncService.movementApplicationUpdated(sqsMessage.Message.fromJson())
              "MOVEMENT_APPLICATION-DELETED" -> syncService.movementApplicationDeleted(sqsMessage.Message.fromJson())
              "MOVEMENT_APPLICATION_MULTI-INSERTED" -> syncService.outsideMovementInserted(sqsMessage.Message.fromJson())
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

  private suspend fun retryMapping(type: String, message: String) = when (ExternalMovementRetryMappingMessageTypes.valueOf(type)) {
    RETRY_MAPPING_TEMPORARY_ABSENCE_APPLICATION -> syncService.retryCreateApplicationMapping(message.fromJson())
    RETRY_MAPPING_TEMPORARY_ABSENCE_OUTSIDE_MOVEMENT -> syncService.retryCreateOutsideMovementMapping(message.fromJson())
  }

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)
}

data class MovementApplicationEvent(
  val movementApplicationId: Long,
  val bookingId: Long,
  val offenderIdDisplay: String,
  override val auditModuleName: String,
) : EventAudited

data class MovementApplicationMultiEvent(
  val movementApplicationMultiId: Long,
  val movementApplicationId: Long,
  val bookingId: Long,
  val offenderIdDisplay: String,
  override val auditModuleName: String,
) : EventAudited

enum class ExternalMovementRetryMappingMessageTypes {
  RETRY_MAPPING_TEMPORARY_ABSENCE_APPLICATION,
  RETRY_MAPPING_TEMPORARY_ABSENCE_OUTSIDE_MOVEMENT,
}
