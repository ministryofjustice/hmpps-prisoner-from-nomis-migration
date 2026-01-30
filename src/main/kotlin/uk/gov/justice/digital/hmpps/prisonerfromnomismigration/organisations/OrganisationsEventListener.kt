package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited.Companion.DPS_SYNC_AUDIT_MODULE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsSynchronisationMessageType.RETRY_SYNCHRONISATION_ADDRESS_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsSynchronisationMessageType.RETRY_SYNCHRONISATION_ADDRESS_PHONE_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsSynchronisationMessageType.RETRY_SYNCHRONISATION_EMAIL_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsSynchronisationMessageType.RETRY_SYNCHRONISATION_ORGANISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsSynchronisationMessageType.RETRY_SYNCHRONISATION_PHONE_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsSynchronisationMessageType.RETRY_SYNCHRONISATION_WEB_MAPPING
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
              "PHONES_CORPORATE-INSERTED" if sqsMessage.Message.asCorporatePhoneEvent().isAddress -> synchronisationService.corporateAddressPhoneInserted(sqsMessage.Message.fromJson())
              "PHONES_CORPORATE-INSERTED" -> synchronisationService.corporatePhoneInserted(sqsMessage.Message.fromJson())
              "PHONES_CORPORATE-UPDATED" if sqsMessage.Message.asCorporatePhoneEvent().isAddress -> synchronisationService.corporateAddressPhoneUpdated(sqsMessage.Message.fromJson())
              "PHONES_CORPORATE-UPDATED" -> synchronisationService.corporatePhoneUpdated(sqsMessage.Message.fromJson())
              "PHONES_CORPORATE-DELETED" if sqsMessage.Message.asCorporatePhoneEvent().isAddress -> synchronisationService.corporateAddressPhoneDeleted(sqsMessage.Message.fromJson())
              "PHONES_CORPORATE-DELETED" -> synchronisationService.corporatePhoneDeleted(sqsMessage.Message.fromJson())
              "INTERNET_ADDRESSES_CORPORATE-INSERTED" -> synchronisationService.corporateInternetAddressInserted(sqsMessage.Message.fromJson())
              "INTERNET_ADDRESSES_CORPORATE-UPDATED" -> synchronisationService.corporateInternetAddressUpdated(sqsMessage.Message.fromJson())
              "INTERNET_ADDRESSES_CORPORATE-DELETED" -> synchronisationService.corporateInternetAddressDeleted(sqsMessage.Message.fromJson())
              "CORPORATE_TYPES-INSERTED" -> synchronisationService.corporateTypeInserted(sqsMessage.Message.fromJson())
              "CORPORATE_TYPES-UPDATED" -> synchronisationService.corporateTypeUpdated(sqsMessage.Message.fromJson())
              "CORPORATE_TYPES-DELETED" -> synchronisationService.corporateTypeDeleted(sqsMessage.Message.fromJson())
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

  private fun String.asCorporatePhoneEvent(): CorporatePhoneEvent = this.fromJson()
  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)

  private suspend fun retryMapping(mappingName: String, message: String) {
    when (OrganisationsSynchronisationMessageType.valueOf(mappingName)) {
      RETRY_SYNCHRONISATION_ORGANISATION_MAPPING -> synchronisationService.retryCreateCorporateMapping(message.fromJson())
      RETRY_SYNCHRONISATION_ADDRESS_MAPPING -> synchronisationService.retryCreateAddressMapping(message.fromJson())
      RETRY_SYNCHRONISATION_PHONE_MAPPING -> synchronisationService.retryCreatePhoneMapping(message.fromJson())
      RETRY_SYNCHRONISATION_ADDRESS_PHONE_MAPPING -> synchronisationService.retryCreateAddressPhoneMapping(message.fromJson())
      RETRY_SYNCHRONISATION_WEB_MAPPING -> synchronisationService.retryCreateWebMapping(message.fromJson())
      RETRY_SYNCHRONISATION_EMAIL_MAPPING -> synchronisationService.retryCreateEmailMapping(message.fromJson())
    }
  }
}

interface CorporateEventAudited : EventAudited {
  override val originatesInDps: Boolean
    get() = auditExactMatchOrHasMissingAudit("${DPS_SYNC_AUDIT_MODULE}_ORGANISATION")
}

data class CorporateEvent(
  override val auditModuleName: String,
  val corporateId: Long,
) : CorporateEventAudited

data class CorporateAddressEvent(
  override val auditModuleName: String,
  val corporateId: Long,
  val addressId: Long,
) : CorporateEventAudited

data class CorporatePhoneEvent(
  override val auditModuleName: String,
  val corporateId: Long,
  val phoneId: Long,
  val isAddress: Boolean,
) : CorporateEventAudited

data class CorporateAddressPhoneEvent(
  override val auditModuleName: String,
  val corporateId: Long,
  val phoneId: Long,
  val addressId: Long,
) : CorporateEventAudited

data class CorporateInternetAddressEvent(
  override val auditModuleName: String,
  val corporateId: Long,
  val internetAddressId: Long,
) : CorporateEventAudited

data class CorporateTypeEvent(
  override val auditModuleName: String,
  val corporateId: Long,
  val corporateType: String,
) : CorporateEventAudited

enum class OrganisationsSynchronisationMessageType {
  RETRY_SYNCHRONISATION_ORGANISATION_MAPPING,
  RETRY_SYNCHRONISATION_ADDRESS_MAPPING,
  RETRY_SYNCHRONISATION_PHONE_MAPPING,
  RETRY_SYNCHRONISATION_ADDRESS_PHONE_MAPPING,
  RETRY_SYNCHRONISATION_WEB_MAPPING,
  RETRY_SYNCHRONISATION_EMAIL_MAPPING,
}
