package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CORE_PERSON_SYNC_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class CorePersonEventListener(
  private val service: CorePersonSynchronisationService,
  private val profileDetailsService: CorePersonSynchronisationProfileDetailsService,
  private val beliefsService: CorePersonSynchronisationBeliefsService,
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(CORE_PERSON_SYNC_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "coreperson")) {
            when (eventType) {
              "OFFENDER-INSERTED" -> service.offenderAdded(sqsMessage.Message.fromJson())
              "OFFENDER-UPDATED" -> service.offenderUpdated(sqsMessage.Message.fromJson())
              "OFFENDER-DELETED" -> service.offenderDeleted(sqsMessage.Message.fromJson())

              "OFFENDER_BOOKING-INSERTED" -> service.offenderBookingAdded(sqsMessage.Message.fromJson())
              "OFFENDER_BOOKING-CHANGED" -> service.offenderBookingUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_BOOKING-REASSIGNED" -> service.offenderBookingReassigned(sqsMessage.Message.fromJson())

              "OFFENDER_SENTENCES-INSERTED" -> service.offenderSentenceAdded(sqsMessage.Message.fromJson())
              "OFFENDER_SENTENCES-UPDATED" -> service.offenderSentenceUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_SENTENCES-DELETED" -> service.offenderSentenceDeleted(sqsMessage.Message.fromJson())

              // TODO: Awaiting SDIT-2482 to be completed for audit module name and sequence number
//              "OFFENDER_IDENTIFIER-INSERTED" -> service.offenderIdentifierAdded(sqsMessage.Message.fromJson())
//              "OFFENDER_IDENTIFIER-UPDATED" -> service.offenderIdentifierUpdated(sqsMessage.Message.fromJson())
//              "OFFENDER_IDENTIFIER-DELETED" -> service.offenderIdentifierDeleted(sqsMessage.Message.fromJson())

              // TODO: Awaiting SDIT-2483 to be completed for new events
              "ADDRESSES_OFFENDER-INSERTED" -> service.offenderAddressAdded(sqsMessage.Message.fromJson())
              "ADDRESSES_OFFENDER-UPDATED" -> service.offenderAddressUpdated(sqsMessage.Message.fromJson())
              "ADDRESSES_OFFENDER-DELETED" -> service.offenderAddressDeleted(sqsMessage.Message.fromJson())

              "OFFENDER_ADDRESS_PHONE-INSERTED" -> service.offenderAddressPhoneAdded(sqsMessage.Message.fromJson())
              "OFFENDER_ADDRESS_PHONE-UPDATED" -> service.offenderAddressPhoneUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_ADDRESS_PHONE-DELETED" -> service.offenderAddressPhoneDeleted(sqsMessage.Message.fromJson())
              "OFFENDER_PHONE-INSERTED" -> service.offenderPhoneAdded(sqsMessage.Message.fromJson())
              "OFFENDER_PHONE-UPDATED" -> service.offenderPhoneUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_PHONE-DELETED" -> service.offenderPhoneDeleted(sqsMessage.Message.fromJson())
              "OFFENDER_EMAIL-INSERTED" -> service.offenderEmailAdded(sqsMessage.Message.fromJson())
              "OFFENDER_EMAIL-UPDATED" -> service.offenderEmailUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_EMAIL-DELETED" -> service.offenderEmailDeleted(sqsMessage.Message.fromJson())

              "OFFENDER_BELIEFS-INSERTED",
              "OFFENDER_BELIEFS-UPDATED",
              "OFFENDER_BELIEFS-DELETED",
              -> beliefsService.offenderBeliefChanged(sqsMessage.Message.fromJson())

              // TODO: Ignore for now - core person haven't mapped address usage
              // If needed then a JIRA is required to add in audit module name - maybe new trigger
//              "ADDRESS_USAGE-INSERTED" -> service.addressUsageAdded(sqsMessage.Message.fromJson())
//              "ADDRESS_USAGE-UPDATED" -> service.addressUsageUpdated(sqsMessage.Message.fromJson())
//              "ADDRESS_USAGE-DELETED" -> service.addressUsageDeleted(sqsMessage.Message.fromJson())

              "OFFENDER_PHYSICAL_DETAILS-CHANGED" -> profileDetailsService.offenderProfileDetailsChanged(sqsMessage.Message.fromJson())
              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }

        else -> log.info("Retry mapping is not written yet for event {}", sqsMessage)
      }
    }
  }
  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

data class OffenderAddressEvent(
  val ownerId: Long,
  val addressId: Long,
  override val auditModuleName: String,
) : EventAudited

data class OffenderEmailEvent(
  val offenderIdDisplay: String,
  val offenderId: Long,
  val internetAddressId: Long,
  override val auditModuleName: String,
) : EventAudited

data class OffenderBeliefEvent(
  val offenderIdDisplay: String,
  val rootOffenderId: Long,
  val offenderBeliefId: Long,
  override val auditModuleName: String,
) : EventAudited

data class OffenderPhoneEvent(
  val offenderIdDisplay: String,
  val offenderId: Long,
  val phoneId: Long,
  override val auditModuleName: String,
) : EventAudited

data class OffenderAddressPhoneEvent(
  val offenderIdDisplay: String,
  val addressId: Long,
  val phoneId: Long,
  override val auditModuleName: String,
) : EventAudited

data class AddressUsageEvent(
  val addressId: Long,
  val addressUsage: String,
  override val auditModuleName: String,
) : EventAudited

data class OffenderIdentifierEvent(
  val offenderIdDisplay: String,
  val offenderId: Long,
  val phoneId: Long,
  override val auditModuleName: String,
) : EventAudited

data class OffenderSentenceEvent(
  val offenderIdDisplay: String,
  val bookingId: Long,
  override val auditModuleName: String,
) : EventAudited

data class OffenderEvent(
  val offenderIdDisplay: String,
  val offenderId: Long,
)

data class OffenderBookingEvent(
  val offenderId: Long,
  val bookingId: Long,
)

data class OffenderBookingReassignedEvent(
  val offenderId: Long,
  val previousOffenderId: Long,
  val bookingId: Long,
)

data class OffenderProfileDetailsEvent(
  val offenderIdDisplay: String,
  val bookingId: Long,
  val profileType: String,
)
