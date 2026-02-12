package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementRetryMappingMessageTypes.RETRY_MAPPING_TEMPORARY_ABSENCE_APPLICATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementRetryMappingMessageTypes.RETRY_MAPPING_TEMPORARY_ABSENCE_EXTERNAL_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementRetryMappingMessageTypes.RETRY_MAPPING_TEMPORARY_ABSENCE_SCHEDULED_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementRetryMappingMessageTypes.RETRY_UPDATE_MAPPING_TEMPORARY_ABSENCE_EXTERNAL_MOVEMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementRetryMappingMessageTypes.RETRY_UPDATE_MAPPING_TEMPORARY_ABSENCE_SCHEDULED_MOVEMENT
import java.util.concurrent.CompletableFuture

@Service
class ExternalMovementsEventListener(
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val syncService: ExternalMovementsSyncService,
  private val moveBookingService: ExternalMovementsMoveBookingService,
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
              "MOVEMENT_APPLICATION-INSERTED" -> syncService.movementApplicationInserted(sqsMessage.Message.fromJson())
              "MOVEMENT_APPLICATION-UPDATED" -> syncService.movementApplicationUpdated(sqsMessage.Message.fromJson())
              "MOVEMENT_APPLICATION-DELETED" -> syncService.movementApplicationDeleted(sqsMessage.Message.fromJson())
              "SCHEDULED_EXT_MOVE-INSERTED" -> syncService.scheduledMovementInserted(sqsMessage.Message.fromJson())
              "SCHEDULED_EXT_MOVE-UPDATED" -> syncService.scheduledMovementUpdated(sqsMessage.Message.fromJson())
              "SCHEDULED_EXT_MOVE-DELETED" -> syncService.scheduledMovementDeleted(sqsMessage.Message.fromJson())
              "EXTERNAL_MOVEMENT-CHANGED" -> syncService.externalMovementChanged(sqsMessage.Message.fromJson())
              "ADDRESSES_OFFENDER-UPDATED" -> syncService.offenderAddressUpdated(sqsMessage.Message.fromJson())
              "ADDRESSES_CORPORATE-UPDATED" -> syncService.corporateAddressUpdated(sqsMessage.Message.fromJson())
              "ADDRESSES_AGENCY-UPDATED" -> syncService.agencyAddressUpdated(sqsMessage.Message.fromJson())
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

  private suspend fun retryMapping(type: String, message: String) = when (ExternalMovementRetryMappingMessageTypes.valueOf(type)) {
    RETRY_MAPPING_TEMPORARY_ABSENCE_APPLICATION -> syncService.retryCreateApplicationMapping(message.fromJson())
    RETRY_MAPPING_TEMPORARY_ABSENCE_SCHEDULED_MOVEMENT -> syncService.retryCreateScheduledMovementMapping(message.fromJson())
    RETRY_MAPPING_TEMPORARY_ABSENCE_EXTERNAL_MOVEMENT -> syncService.retryCreateExternalMovementMapping(message.fromJson())
    RETRY_UPDATE_MAPPING_TEMPORARY_ABSENCE_EXTERNAL_MOVEMENT -> syncService.retryUpdateExternalMovementMapping(message.fromJson())
    RETRY_UPDATE_MAPPING_TEMPORARY_ABSENCE_SCHEDULED_MOVEMENT -> syncService.retryUpdateScheduledMovementMapping(message.fromJson())
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

data class MovementApplicationEvent(
  val movementApplicationId: Long,
  val bookingId: Long,
  val offenderIdDisplay: String,
  override val auditModuleName: String,
) : EventAudited

data class ScheduledMovementEvent(
  val eventId: Long,
  val bookingId: Long,
  val offenderIdDisplay: String,
  val eventMovementType: MovementType,
  val directionCode: DirectionCode,
  override val auditModuleName: String,
) : EventAudited

data class ExternalMovementEvent(
  val bookingId: Long,
  // Some external movements don't have an offenderIdDisplay (REL / TRN) though TAPs always do
  val offenderIdDisplay: String?,
  val movementSeq: Int,
  val movementType: MovementType,
  val directionCode: DirectionCode,
  val recordInserted: Boolean,
  val recordDeleted: Boolean,
  override val auditModuleName: String,
) : EventAudited

data class OffenderAddressUpdatedEvent(
  val eventType: String,
  val offenderId: Long,
  val addressId: Long,
  val nomisEventType: String,
  override val auditModuleName: String,
) : EventAudited

data class CorporateAddressUpdatedEvent(
  val eventType: String,
  val corporateId: Long,
  val addressId: Long,
  val nomisEventType: String,
  override val auditModuleName: String,
) : EventAudited

data class AgencyAddressUpdatedEvent(
  val eventType: String,
  val agencyCode: String,
  val addressId: Long,
  val nomisEventType: String,
  override val auditModuleName: String,
) : EventAudited

enum class DirectionCode { IN, OUT }
enum class MovementType { ADM, CRT, REL, TAP, TRN, }

enum class ExternalMovementRetryMappingMessageTypes {
  RETRY_MAPPING_TEMPORARY_ABSENCE_APPLICATION,
  RETRY_MAPPING_TEMPORARY_ABSENCE_SCHEDULED_MOVEMENT,
  RETRY_MAPPING_TEMPORARY_ABSENCE_EXTERNAL_MOVEMENT,
  RETRY_UPDATE_MAPPING_TEMPORARY_ABSENCE_SCHEDULED_MOVEMENT,
  RETRY_UPDATE_MAPPING_TEMPORARY_ABSENCE_EXTERNAL_MOVEMENT,
}
