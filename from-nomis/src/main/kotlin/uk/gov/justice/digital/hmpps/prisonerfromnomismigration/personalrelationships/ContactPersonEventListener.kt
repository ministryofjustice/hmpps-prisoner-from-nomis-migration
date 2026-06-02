package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonPrisonerMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RESYNCHRONISE_MOVE_BOOKING_CONTACTPERSON_TARGET
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RESYNCHRONISE_MOVE_BOOKING_PRISONER_RESTRICTION_TARGET
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RETRY_REPLACE_PRISONER_PERSON_BOOKING_CHANGED_MAPPINGS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RETRY_REPLACE_PRISONER_PERSON_BOOKING_MOVED_MAPPINGS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RETRY_REPLACE_PRISONER_PERSON_PRISONER_MERGED_MAPPINGS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_ADDRESS_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_CONTACT_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_CONTACT_RESTRICTION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_EMAIL_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_EMPLOYMENT_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_IDENTIFIER_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_PERSON_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_PERSON_RESTRICTION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_PHONE_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonSynchronisationMessageType.RETRY_SYNCHRONISATION_PRISONER_RESTRICTION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails.ContactPersonProfileDetailsSyncService
import java.util.concurrent.CompletableFuture

@Service
class ContactPersonEventListener(
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val service: ContactPersonSynchronisationService,
  private val profileDetailService: ContactPersonProfileDetailsSyncService,
  private val prisonerRestrictionSynchronisationService: PrisonerRestrictionSynchronisationService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventpersonalrelationships", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "personalrelationships")) {
            when (eventType) {
              "PERSON-INSERTED" -> service.personAdded(sqsMessage.Message.fromJson())
              "PERSON-UPDATED" -> service.personUpdated(sqsMessage.Message.fromJson())
              "PERSON-DELETED" -> service.personDeleted(sqsMessage.Message.fromJson())
              "ADDRESSES_PERSON-INSERTED" -> service.personAddressAdded(sqsMessage.Message.fromJson())
              "ADDRESSES_PERSON-UPDATED" -> service.personAddressUpdated(sqsMessage.Message.fromJson())
              "ADDRESSES_PERSON-DELETED" -> service.personAddressDeleted(sqsMessage.Message.fromJson())
              "PHONES_PERSON-INSERTED" -> service.personPhoneAdded(sqsMessage.Message.fromJson())
              "PHONES_PERSON-UPDATED" -> service.personPhoneUpdated(sqsMessage.Message.fromJson())
              "PHONES_PERSON-DELETED" -> service.personPhoneDeleted(sqsMessage.Message.fromJson())
              "INTERNET_ADDRESSES_PERSON-INSERTED" -> service.personEmailAdded(sqsMessage.Message.fromJson())
              "INTERNET_ADDRESSES_PERSON-UPDATED" -> service.personEmailUpdated(sqsMessage.Message.fromJson())
              "INTERNET_ADDRESSES_PERSON-DELETED" -> service.personEmailDeleted(sqsMessage.Message.fromJson())
              "VISITOR_RESTRICTION-UPSERTED" -> service.personRestrictionUpserted(sqsMessage.Message.fromJson())
              "VISITOR_RESTRICTION-DELETED" -> service.personRestrictionDeleted(sqsMessage.Message.fromJson())
              "OFFENDER_CONTACT-INSERTED" -> service.contactAdded(sqsMessage.Message.fromJson())
              "OFFENDER_CONTACT-UPDATED" -> service.contactUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_CONTACT-DELETED" -> service.contactDeleted(sqsMessage.Message.fromJson())
              "PERSON_RESTRICTION-UPSERTED" -> service.contactRestrictionUpserted(sqsMessage.Message.fromJson())
              "PERSON_RESTRICTION-DELETED" -> service.contactRestrictionDeleted(sqsMessage.Message.fromJson())
              "PERSON_EMPLOYMENTS-INSERTED" -> service.personEmploymentAdded(sqsMessage.Message.fromJson())
              "PERSON_EMPLOYMENTS-UPDATED" -> service.personEmploymentUpdated(sqsMessage.Message.fromJson())
              "PERSON_EMPLOYMENTS-DELETED" -> service.personEmploymentDeleted(sqsMessage.Message.fromJson())
              "PERSON_IDENTIFIERS-INSERTED" -> service.personIdentifierAdded(sqsMessage.Message.fromJson())
              "PERSON_IDENTIFIERS-UPDATED" -> service.personIdentifierUpdated(sqsMessage.Message.fromJson())
              "PERSON_IDENTIFIERS-DELETED" -> service.personIdentifierDeleted(sqsMessage.Message.fromJson())
              "BOOKING-DELETED" -> service.resetPrisonerContactsForBookingDeleted(sqsMessage.Message.fromJson())
              "OFFENDER_PHYSICAL_DETAILS-CHANGED" -> profileDetailService.profileDetailsChanged(sqsMessage.Message.fromJson())
              "RESTRICTION-UPSERTED" -> prisonerRestrictionSynchronisationService.prisonerRestrictionUpserted(sqsMessage.Message.fromJson())
              "RESTRICTION-DELETED" -> prisonerRestrictionSynchronisationService.prisonerRestrictionDeleted(sqsMessage.Message.fromJson())
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
    when (ContactPersonSynchronisationMessageType.valueOf(mappingName)) {
      RETRY_SYNCHRONISATION_PERSON_MAPPING -> service.retryCreatePersonMapping(message.fromJson())
      RETRY_SYNCHRONISATION_CONTACT_MAPPING -> service.retryCreateContactMapping(message.fromJson())
      RETRY_SYNCHRONISATION_ADDRESS_MAPPING -> service.retryCreateAddressMapping(message.fromJson())
      RETRY_SYNCHRONISATION_EMAIL_MAPPING -> service.retryCreateEmailMapping(message.fromJson())
      RETRY_SYNCHRONISATION_PHONE_MAPPING -> service.retryCreatePhoneMapping(message.fromJson())
      RETRY_SYNCHRONISATION_IDENTIFIER_MAPPING -> service.retryCreateIdentifierMapping(message.fromJson())
      RETRY_SYNCHRONISATION_CONTACT_RESTRICTION_MAPPING -> service.retryCreateContactRestrictionMapping(message.fromJson())
      RETRY_SYNCHRONISATION_PERSON_RESTRICTION_MAPPING -> service.retryCreatePersonRestrictionMapping(message.fromJson())
      RETRY_SYNCHRONISATION_EMPLOYMENT_MAPPING -> service.retryCreateEmploymentMapping(message.fromJson())
      RETRY_REPLACE_PRISONER_PERSON_PRISONER_MERGED_MAPPINGS -> service.retryReplacePrisonerPersonPrisonerMergedMappings(message.fromJson())
      RETRY_REPLACE_PRISONER_PERSON_BOOKING_CHANGED_MAPPINGS -> service.retryReplacePrisonerPersonBookingChangeMappings(message.fromJson())
      RETRY_REPLACE_PRISONER_PERSON_BOOKING_MOVED_MAPPINGS -> service.retryReplacePrisonerPersonBookingMovedMappings(message.fromJson())
      RESYNCHRONISE_MOVE_BOOKING_CONTACTPERSON_TARGET -> service.resetAfterPrisonerBookingMovedIfNecessary(message.fromJson())
      RETRY_SYNCHRONISATION_PRISONER_RESTRICTION_MAPPING -> prisonerRestrictionSynchronisationService.retryCreatePrisonerRestrictionMapping(message.fromJson())
      RESYNCHRONISE_MOVE_BOOKING_PRISONER_RESTRICTION_TARGET -> prisonerRestrictionSynchronisationService.resetAfterPrisonerBookingMovedIfNecessary(message.fromJson())
    }
  }
}

data class PersonRestrictionEvent(
  val visitorRestrictionId: Long,
  val personId: Long,
  override val auditModuleName: String,
) : EventAudited

data class ContactEvent(
  val offenderIdDisplay: String,
  val bookingId: Long,
  val contactId: Long,
  val personId: Long?,
  override val auditModuleName: String,
) : EventAudited

data class ContactRestrictionEvent(
  val offenderPersonRestrictionId: Long,
  val offenderIdDisplay: String?,
  val personId: Long,
  val contactPersonId: Long,
  override val auditModuleName: String,
) : EventAudited

data class PersonEvent(
  val personId: Long,
  override val auditModuleName: String,
) : EventAudited

data class PersonAddressEvent(
  val personId: Long,
  val addressId: Long,
  override val auditModuleName: String,
) : EventAudited

data class PersonPhoneEvent(
  val personId: Long,
  val phoneId: Long,
  val addressId: Long?,
  override val auditModuleName: String,
  val isAddress: Boolean,
) : EventAudited

data class PersonInternetAddressEvent(
  val personId: Long,
  val internetAddressId: Long,
  override val auditModuleName: String,
) : EventAudited

data class PersonEmploymentEvent(
  val personId: Long,
  val employmentSequence: Long,
  override val auditModuleName: String,
) : EventAudited

data class PersonIdentifierEvent(
  val personId: Long,
  val identifierSequence: Long,
  override val auditModuleName: String,
) : EventAudited

data class ProfileDetailsChangedEvent(
  val offenderIdDisplay: String,
  val bookingId: Long,
  val profileType: String,
)

// TODO - remove defaults after trigger change
data class PrisonerRestrictionEvent(
  val offenderIdDisplay: String,
  val offenderRestrictionId: Long,
  val isUpdated: Boolean = false,
  override val auditModuleName: String = "NOMIS",
) : EventAudited

data class ContactPersonPrisonerMappings(
  val mappings: ContactPersonPrisonerMappingsDto,
  val offenderNo: String,
)

enum class ContactPersonSynchronisationMessageType {
  RETRY_SYNCHRONISATION_PERSON_MAPPING,
  RETRY_SYNCHRONISATION_CONTACT_MAPPING,
  RETRY_SYNCHRONISATION_ADDRESS_MAPPING,
  RETRY_SYNCHRONISATION_EMAIL_MAPPING,
  RETRY_SYNCHRONISATION_PHONE_MAPPING,
  RETRY_SYNCHRONISATION_IDENTIFIER_MAPPING,
  RETRY_SYNCHRONISATION_EMPLOYMENT_MAPPING,
  RETRY_SYNCHRONISATION_CONTACT_RESTRICTION_MAPPING,
  RETRY_SYNCHRONISATION_PERSON_RESTRICTION_MAPPING,
  RETRY_REPLACE_PRISONER_PERSON_PRISONER_MERGED_MAPPINGS,
  RETRY_REPLACE_PRISONER_PERSON_BOOKING_CHANGED_MAPPINGS,
  RETRY_REPLACE_PRISONER_PERSON_BOOKING_MOVED_MAPPINGS,
  RESYNCHRONISE_MOVE_BOOKING_CONTACTPERSON_TARGET,
  RESYNCHRONISE_MOVE_BOOKING_PRISONER_RESTRICTION_TARGET,
  RETRY_SYNCHRONISATION_PRISONER_RESTRICTION_MAPPING,
}
