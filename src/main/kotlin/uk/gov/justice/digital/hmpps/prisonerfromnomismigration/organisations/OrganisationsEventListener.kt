package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsSynchronisationMessageType.RETRY_SYNCHRONISATION_ORGANISATION_MAPPING
import java.util.concurrent.CompletableFuture

@Service
class OrganisationsEventListener(
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val synchronisationService: OrganisationsSynchronisationService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @Suppress("LoggingSimilarMessage")
  @SqsListener("eventorganisations", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "organisations")) {
            when (eventType) {
              "CORPORATE-INSERTED" -> synchronisationService.corporateInserted(sqsMessage.Message.fromJson())
              "CORPORATE-UPDATED" -> synchronisationService.corporateUpdated(sqsMessage.Message.fromJson())
              "CORPORATE-DELETED" -> synchronisationService.corporateDeleted(sqsMessage.Message.fromJson())
              "ADDRESSES_CORPORATE-INSERTED" -> synchronisationService.corporateAddressInserted(sqsMessage.Message.fromJson())
              "ADDRESSES_CORPORATE-UPDATED" -> synchronisationService.corporateAddressUpdated(sqsMessage.Message.fromJson())
              "ADDRESSES_CORPORATE-DELETED" -> synchronisationService.corporateAddressDeleted(sqsMessage.Message.fromJson())
              "PHONES_CORPORATE-INSERTED" -> log.debug("Received $eventType")
              "PHONES_CORPORATE-UPDATED" -> log.debug("Received $eventType")
              "PHONES_CORPORATE-DELETED" -> log.debug("Received $eventType")
              "INTERNET_ADDRESSES_CORPORATE-INSERTED" -> log.debug("Received $eventType")
              "INTERNET_ADDRESSES_CORPORATE-UPDATED" -> log.debug("Received $eventType")
              "INTERNET_ADDRESSES_CORPORATE-DELETED" -> log.debug("Received $eventType")
              "CORPORATE_TYPES-INSERTED" -> log.debug("Received $eventType")
              "CORPORATE_TYPES-UPDATED" -> log.debug("Received $eventType")
              "CORPORATE_TYPES-DELETED" -> log.debug("Received $eventType")

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

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)

  private suspend fun retryMapping(mappingName: String, message: String) {
    when (OrganisationsSynchronisationMessageType.valueOf(mappingName)) {
      RETRY_SYNCHRONISATION_ORGANISATION_MAPPING -> synchronisationService.retryCreateCorporateMapping(message.fromJson())
    }
  }
}

data class CorporateEvent(
  override val auditModuleName: String,
  val corporateId: Long,
) : EventAudited

data class CorporateAddressEvent(
  override val auditModuleName: String,
  val corporateId: Long,
  val addressId: Long,
) : EventAudited

enum class OrganisationsSynchronisationMessageType {
  RETRY_SYNCHRONISATION_ORGANISATION_MAPPING,
}
