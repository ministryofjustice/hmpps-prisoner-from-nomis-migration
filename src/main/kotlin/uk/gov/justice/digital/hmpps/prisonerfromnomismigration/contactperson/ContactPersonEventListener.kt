package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.contactperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import java.util.concurrent.CompletableFuture

@Service
class ContactPersonEventListener(
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val service: ContactPersonSynchronisationService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventcontactperson", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "contactperson")) {
            when (eventType) {
              "PERSON-INSERTED" -> service.personAdded(sqsMessage.Message.fromJson())
              "PERSON-UPDATED" -> service.personUpdated(sqsMessage.Message.fromJson())
              "PERSON-DELETED" -> service.personDeleted(sqsMessage.Message.fromJson())
              "VISITOR_RESTRICTION-UPSERTED" -> service.personRestrictionUpserted(sqsMessage.Message.fromJson())
              "VISITOR_RESTRICTION-DELETED" -> service.personRestrictionDeleted(sqsMessage.Message.fromJson())
              "OFFENDER_CONTACT-INSERTED" -> service.contactAdded(sqsMessage.Message.fromJson())
              "OFFENDER_CONTACT-UPDATED" -> service.contactUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_CONTACT-DELETED" -> service.contactDeleted(sqsMessage.Message.fromJson())
              "PERSON_RESTRICTION-UPSERTED" -> service.contactRestrictionUpserted(sqsMessage.Message.fromJson())
              "PERSON_RESTRICTION-DELETED" -> service.contactRestrictionDeleted(sqsMessage.Message.fromJson())
              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }
      }
    }
  }
  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

data class PersonRestrictionEvent(
  val visitorRestrictionId: Long,
  val personId: Long,
  val auditModuleName: String,
)

data class ContactEvent(
  val offenderIdDisplay: String,
  val bookingId: Long,
  val contactId: Long,
  val personId: Long,
  val auditModuleName: String,
)

data class ContactRestrictionEvent(
  val offenderPersonRestrictionId: Long,
  val offenderIdDisplay: String,
  val personId: Long,
  val contactPersonId: Long,
  val auditModuleName: String,
)

data class PersonEvent(
  val personId: Long,
  val auditModuleName: String,
)
