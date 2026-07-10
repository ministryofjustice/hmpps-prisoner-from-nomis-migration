package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

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
import java.util.concurrent.CompletableFuture

@Service
class StaffEventListener(
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val service: StaffSynchronisationService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventstaff", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "staff")) {
            when (eventType) {
              "STAFF_MEMBERS-INSERTED" -> service.staffUpserted("created", sqsMessage.Message.fromJson())
              "STAFF_MEMBERS-UPDATED" -> service.staffUpserted("updated", sqsMessage.Message.fromJson())
              "STAFF_MEMBERS-DELETED" -> service.staffDeleted(sqsMessage.Message.fromJson())
              "STAFF_USER_ACCOUNTS-INSERTED" -> service.staffAccountUpserted("created", sqsMessage.Message.fromJson())
              "STAFF_USER_ACCOUNTS-UPDATED" -> service.staffAccountUpserted("updated", sqsMessage.Message.fromJson())
              "STAFF_USER_ACCOUNTS-DELETED" -> service.staffAccountUpserted("deleted", sqsMessage.Message.fromJson())
              "INTERNET_ADDRESSES_STAFF-INSERTED" -> service.staffInternetAddressUpserted("created", sqsMessage.Message.fromJson())
              "INTERNET_ADDRESSES_STAFF-UPDATED" -> service.staffInternetAddressUpserted("updated", sqsMessage.Message.fromJson())
              "INTERNET_ADDRESSES_STAFF-DELETED" -> service.staffInternetAddressUpserted("deleted", sqsMessage.Message.fromJson())
              "USER_ACCESSIBLE_CASELOADS-INSERTED" -> service.userAccessibleCaseloadUpserted("created", sqsMessage.Message.fromJson())
              "USER_ACCESSIBLE_CASELOADS-DELETED" -> service.userAccessibleCaseloadUpserted("deleted", sqsMessage.Message.fromJson())
              "USER_CASELOAD_ROLES-INSERTED" -> service.userCaseloadRoleUpserted("created", sqsMessage.Message.fromJson())
              "USER_CASELOAD_ROLES-DELETED" -> service.userCaseloadRoleUpserted("deleted", sqsMessage.Message.fromJson())

              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }
      }
    }
  }
  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

interface StaffAuditedEvent : EventAudited {
  val staffId: Long
}
interface UsernameAuditedEvent : EventAudited {
  val username: String
}

data class StaffEvent(
  override val staffId: Long,
  override val auditModuleName: String,
) : StaffAuditedEvent

data class StaffUserAccountEvent(
  override val staffId: Long,
  val username: String,
  override val auditModuleName: String,
) : StaffAuditedEvent

data class StaffInternetAddressEvent(
  override val staffId: Long,
  val internetAddressId: Long,
  override val auditModuleName: String,
) : StaffAuditedEvent

data class UserAccessibleCaseloadEvent(
  override val username: String,
  val caseloadId: String,
  override val auditModuleName: String,
) : UsernameAuditedEvent

data class UserCaseloadRoleEvent(
  override val username: String,
  val caseloadId: String,
  val roleCode: String,
  override val auditModuleName: String,
) : UsernameAuditedEvent
