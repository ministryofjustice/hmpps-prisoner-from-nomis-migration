package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapRetryMappingMessageTypes.RETRY_MAPPING_TAP_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapRetryMappingMessageTypes.RETRY_MOVE_BOOKING_MAPPING_TAP
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps.TapRetryMappingMessageTypes.RETRY_UPDATE_MAPPING_TAP_MOVEMENT
import java.util.concurrent.CompletableFuture

@Service
class TapEventListener(
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val moveBookingService: TapMoveBookingService,
  private val tapMovementService: TapMovementService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventexternalmovements", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "externalmovements")) {
            @Suppress("UNUSED_EXPRESSION")
            when (eventType) {
              "EXTERNAL_MOVEMENT-CHANGED" -> tapMovementService.tapMovementChanged(sqsMessage.Message.fromJson())
              "prison-offender-events.prisoner.booking.moved" -> moveBookingService.moveBooking(sqsMessage.Message.fromJson())
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

  private suspend fun retryMapping(type: String, message: String) = when (TapRetryMappingMessageTypes.valueOf(type)) {
    RETRY_MAPPING_TAP_MOVEMENT -> tapMovementService.retryCreateExternalMovementMapping(message.fromJson())
    RETRY_UPDATE_MAPPING_TAP_MOVEMENT -> tapMovementService.retryUpdateExternalMovementMapping(message.fromJson())
    RETRY_MOVE_BOOKING_MAPPING_TAP -> moveBookingService.retryMoveBookingMapping(message.fromJson())
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

enum class TapRetryMappingMessageTypes {
  RETRY_MAPPING_TAP_MOVEMENT,
  RETRY_UPDATE_MAPPING_TAP_MOVEMENT,
  RETRY_MOVE_BOOKING_MAPPING_TAP,
}
