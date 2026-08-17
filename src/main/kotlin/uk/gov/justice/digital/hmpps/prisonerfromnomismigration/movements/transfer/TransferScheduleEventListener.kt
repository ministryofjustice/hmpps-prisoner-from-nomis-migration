package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.transfer.TransfersRetryMappingMessageTypes.RETRY_MAPPING_TRANSFER_SCHEDULE
import java.util.concurrent.CompletableFuture

@Service
class TransferScheduleEventListener(
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val transferScheduleService: TransferScheduleSyncScheduleService,
  private val transferMovementService: TransferScheduleSyncMovementService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventtransfermovements", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "transfermovements")) {
            @Suppress("UNUSED_EXPRESSION")
            when (eventType) {
              "SCHEDULED_EXT_MOVE-INSERTED" -> transferScheduleService.scheduledMovementInserted(sqsMessage.Message.fromJson())
              "SCHEDULED_EXT_MOVE-UPDATED" -> transferScheduleService.scheduledMovementUpdated(sqsMessage.Message.fromJson())
              "SCHEDULED_EXT_MOVE-DELETED" -> transferScheduleService.transferScheduleDeleted(sqsMessage.Message.fromJson())
              "TRANSFER_WAITLIST-INSERTED", "TRANSFER_WAITLIST-UPDATED", "TRANSFER_WAITLIST-DELETED" -> transferScheduleService.transferWaitlistChanged(sqsMessage.Message.fromJson())
              "EXTERNAL_MOVEMENT-CHANGED" -> transferMovementService.transferMovementChanged(sqsMessage.Message.fromJson())
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

  private suspend fun retryMapping(type: String, message: String) = when (TransfersRetryMappingMessageTypes.valueOf(type)) {
    RETRY_MAPPING_TRANSFER_SCHEDULE -> transferScheduleService.retryCreateScheduleMapping(message.fromJson())
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

enum class TransfersRetryMappingMessageTypes {
  RETRY_MAPPING_TRANSFER_SCHEDULE,
}

data class TransferWaitlistEvent(
  val eventId: Long,
  val bookingId: Long,
  val offenderIdDisplay: String,
  override val auditModuleName: String,
) : EventAudited
