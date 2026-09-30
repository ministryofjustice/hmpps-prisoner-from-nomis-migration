package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonSynchronisationMessageType.RETRY_SYNCHRONISATION_ADDRESS_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonSynchronisationMessageType.RETRY_SYNCHRONISATION_EMAIL_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonSynchronisationMessageType.RETRY_SYNCHRONISATION_PHONE_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonSynchronisationMessageType.RETRY_SYNCHRONISATION_RELIGION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.religion.CorePersonSynchronisationBeliefsService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CORE_PERSON_SYNC_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class CorePersonEventListener(
  private val service: CorePersonSynchronisationService,
  private val beliefsService: CorePersonSynchronisationBeliefsService,
  private val addressContactService: CorePersonSynchronisationAddressContactService,
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
              "OFFENDER_BELIEFS-INSERTED" -> beliefsService.offenderBeliefCreated(sqsMessage.Message.fromJson())
              "OFFENDER_BELIEFS-UPDATED" -> beliefsService.offenderBeliefUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_BELIEFS-DELETED" -> beliefsService.offenderBeliefDeleted(sqsMessage.Message.fromJson())

              "ADDRESSES_OFFENDER-INSERTED" -> addressContactService.offenderAddressAdded(sqsMessage.Message.fromJson())
              "ADDRESSES_OFFENDER-UPDATED" -> addressContactService.offenderAddressUpdated(sqsMessage.Message.fromJson())
              "ADDRESSES_OFFENDER-DELETED" -> addressContactService.offenderAddressDeleted(sqsMessage.Message.fromJson())
              "OFFENDER_ADDRESS_PHONE-INSERTED" -> addressContactService.offenderPhoneAdded(sqsMessage.Message.fromJson())
              "OFFENDER_ADDRESS_PHONE-UPDATED" -> addressContactService.offenderPhoneUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_ADDRESS_PHONE-DELETED" -> addressContactService.offenderPhoneDeleted(sqsMessage.Message.fromJson())
              "OFFENDER_PHONE-INSERTED" -> addressContactService.offenderPhoneAdded(sqsMessage.Message.fromJson())
              "OFFENDER_PHONE-UPDATED" -> addressContactService.offenderPhoneUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_PHONE-DELETED" -> addressContactService.offenderPhoneDeleted(sqsMessage.Message.fromJson())

              "OFFENDER_EMAIL-INSERTED" -> addressContactService.offenderEmailAdded(sqsMessage.Message.fromJson())
              "OFFENDER_EMAIL-UPDATED" -> addressContactService.offenderEmailUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_EMAIL-DELETED" -> addressContactService.offenderEmailDeleted(sqsMessage.Message.fromJson())

              "prison-offender-events.prisoner.merged" -> service.synchronisePrisonerMerge(sqsMessage.Message.fromJson())
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
  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
  private suspend fun retryMapping(mappingName: String, message: String) {
    when (CorePersonSynchronisationMessageType.valueOf(mappingName)) {
      RETRY_SYNCHRONISATION_RELIGION_MAPPING -> beliefsService.retryCreateMapping(message.fromJson())
      RETRY_SYNCHRONISATION_EMAIL_MAPPING -> addressContactService.retryCreateEmailMapping(message.fromJson())
      RETRY_SYNCHRONISATION_ADDRESS_MAPPING -> addressContactService.retryCreateAddressMapping(message.fromJson())
      RETRY_SYNCHRONISATION_PHONE_MAPPING -> addressContactService.retryCreatePhoneMapping(message.fromJson())
    }
  }
}

enum class CorePersonSynchronisationMessageType {
  RETRY_SYNCHRONISATION_RELIGION_MAPPING,
  RETRY_SYNCHRONISATION_EMAIL_MAPPING,
  RETRY_SYNCHRONISATION_ADDRESS_MAPPING,
  RETRY_SYNCHRONISATION_PHONE_MAPPING,
}

data class OffenderBeliefEvent(
  val offenderIdDisplay: String,
  val rootOffenderId: Long,
  val offenderBeliefId: Long,
  override val auditModuleName: String,
) : EventAudited

data class OffenderAddressEvent(
  val offenderIdDisplay: String,
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
  val addressId: Long?,
  val phoneId: Long,
  override val auditModuleName: String,
) : EventAudited
