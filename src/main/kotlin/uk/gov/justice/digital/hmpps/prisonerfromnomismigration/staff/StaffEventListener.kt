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
              "STAFF_MEMBERS-INSERTED" -> service.staffCreated(sqsMessage.Message.fromJson())
              "STAFF_MEMBERS-UPDATED" -> service.staffUpdated(sqsMessage.Message.fromJson())
              "STAFF_MEMBERS-DELETED" -> service.staffDeleted(sqsMessage.Message.fromJson())
              "STAFF_USER_ACCOUNTS-INSERTED" -> service.staffAccountCreated(sqsMessage.Message.fromJson())
              "STAFF_USER_ACCOUNTS-UPDATED" -> service.staffAccountUpdated(sqsMessage.Message.fromJson())
              "STAFF_USER_ACCOUNTS-DELETED" -> service.staffAccountDeleted(sqsMessage.Message.fromJson())
              "INTERNET_ADDRESSES_STAFF-INSERTED" -> service.staffInternetAddressCreated(sqsMessage.Message.fromJson())
              "INTERNET_ADDRESSES_STAFF-UPDATED" -> service.staffInternetAddressUpdated(sqsMessage.Message.fromJson())
              "INTERNET_ADDRESSES_STAFF-DELETED" -> service.staffInternetAddressDeleted(sqsMessage.Message.fromJson())
              "USER_ACCESSIBLE_CASELOADS-INSERTED" -> service.userAccessibleCaseloadCreated(sqsMessage.Message.fromJson())
              "USER_ACCESSIBLE_CASELOADS-DELETED" -> service.userAccessibleCaseloadDeleted(sqsMessage.Message.fromJson())

              /*
                TODO
                USER_CASELOAD_ROLES
               */
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

data class StaffEvent(
  val staffId: Long,
  override val auditModuleName: String,
) : EventAudited

data class StaffUserAccountEvent(
  val staffId: Long,
  val username: String,
  override val auditModuleName: String,
) : EventAudited

data class StaffInternetAddressEvent(
  val staffId: Long,
  val internetAddressId: Long,
  override val auditModuleName: String,
) : EventAudited

data class UserAccessibleCaseloadEvent(
  val username: String,
  val caseloadId: String,
  override val auditModuleName: String,
) : EventAudited
