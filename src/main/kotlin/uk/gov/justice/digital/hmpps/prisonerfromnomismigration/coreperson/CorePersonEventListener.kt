package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import java.util.concurrent.CompletableFuture

@Service
class CorePersonEventListener(
  private val service: CorePersonSynchronisationService,
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

//  @SqsListener("eventcoreperson", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "coreperson")) {
            when (eventType) {
//              "OFFENDER_INSERTED" ->service.offenderAdded(sqsMessage.Message.fromJson())
//              "OFFENDER_UPDATED" -> service.offenderUpdated(sqsMessage.Message.fromJson())
//              "OFFENDER_DELETED" -> service.offenderDeleted(sqsMessage.Message.fromJson())
//              "OFFENDER_IDENTIFIER-INSERTED" ->service.offenderIdentifierAdded(sqsMessage.Message.fromJson())
//              "OFFENDER_IDENTIFIER-UPDATED" -> service.offenderIdentifierUpdated(sqsMessage.Message.fromJson())
//              "OFFENDER_IDENTIFIER-DELETED" -> service.offenderIdentifierDeleted(sqsMessage.Message.fromJson())
              "OFFENDER_ADDRESS-INSERTED" -> service.offenderAddressAdded(sqsMessage.Message.fromJson())
              "OFFENDER_ADDRESS-UPDATED" -> service.offenderAddressUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_ADDRESS-DELETED" -> service.offenderAddressDeleted(sqsMessage.Message.fromJson())
              "OFFENDER_ADDRESS_PHONE-INSERTED" -> service.offenderAddressPhoneAdded(sqsMessage.Message.fromJson())
              "OFFENDER_ADDRESS_PHONE-UPDATED" -> service.offenderAddressPhoneUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_ADDRESS_PHONE-DELETED" -> service.offenderAddressPhoneDeleted(sqsMessage.Message.fromJson())
              "OFFENDER_PHONE-INSERTED" -> service.offenderPhoneAdded(sqsMessage.Message.fromJson())
              "OFFENDER_PHONE-UPDATED" -> service.offenderPhoneUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_PHONE-DELETED" -> service.offenderPhoneDeleted(sqsMessage.Message.fromJson())
              "OFFENDER_EMAIL-INSERTED" -> service.offenderEmailAdded(sqsMessage.Message.fromJson())
              "OFFENDER_EMAIL-UPDATED" -> service.offenderEmailUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_EMAIL-DELETED" -> service.offenderEmailDeleted(sqsMessage.Message.fromJson())
              "ADDRESS_USAGE-INSERTED" -> service.addressUsageAdded(sqsMessage.Message.fromJson())
              "ADDRESS_USAGE-UPDATED" -> service.addressUsageUpdated(sqsMessage.Message.fromJson())
              "ADDRESS_USAGE-DELETED" -> service.addressUsageDeleted(sqsMessage.Message.fromJson())
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
  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)
}

interface EventAudited {
  val auditModuleName: String
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
