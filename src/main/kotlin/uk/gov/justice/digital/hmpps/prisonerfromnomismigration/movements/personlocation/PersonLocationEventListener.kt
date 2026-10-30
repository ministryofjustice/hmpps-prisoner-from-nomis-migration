package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationRetryMappingMessageTypes.RETRY_MAPPING_PERSON_LOCATION_BOOKING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.personlocation.PersonLocationRetryMappingMessageTypes.RETRY_MAPPING_PERSON_LOCATION_MOVEMENT
import java.util.concurrent.CompletableFuture

@Service
class PersonLocationEventListener(
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val syncMovementService: PersonLocationSyncMovementService,
  private val syncBookingService: PersonLocationSyncBookingService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventpersonlocations", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "personlocations")) {
            when (eventType) {
              "EXTERNAL_MOVEMENT-CHANGED" -> syncMovementService.externalMovementChanged(sqsMessage.Message.fromJson())
              "BOOKING_STATUS-INSERTED" -> syncBookingService.bookingInserted(sqsMessage.Message.fromJson())
              "BOOKING_STATUS-UPDATED" -> syncBookingService.bookingUpdated(sqsMessage.Message.fromJson())
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

  private suspend fun retryMapping(type: String, message: String) = when (PersonLocationRetryMappingMessageTypes.valueOf(type)) {
    RETRY_MAPPING_PERSON_LOCATION_BOOKING -> syncBookingService.retryCreateBookingMapping(message.fromJson())
    RETRY_MAPPING_PERSON_LOCATION_MOVEMENT -> syncMovementService.retryCreateMovementMapping(message.fromJson())
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

enum class PersonLocationRetryMappingMessageTypes {
  RETRY_MAPPING_PERSON_LOCATION_BOOKING,
  RETRY_MAPPING_PERSON_LOCATION_MOVEMENT,
}
