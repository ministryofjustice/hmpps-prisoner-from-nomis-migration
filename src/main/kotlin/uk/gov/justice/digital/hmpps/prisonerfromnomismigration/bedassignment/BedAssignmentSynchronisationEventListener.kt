package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.BED_ASSIGNMENT_SYNC_QUEUE_ID
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

@Service
class BedAssignmentSynchronisationEventListener(
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val syncService: BedAssignmentSyncService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(BED_ASSIGNMENT_SYNC_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> = asCompletableFuture {
    log.debug("Received bed assignment event message {}", message)
    val sqsMessage = jsonMapper.readValue<SQSMessage>(message)
    when (sqsMessage.Type) {
      "Notification" -> {
        val eventType = sqsMessage.MessageAttributes!!.eventType.Value
        if (eventFeatureSwitch.isEnabled(eventType, "bedassignment")) {
          val event = jsonMapper.readValue<BedAssignmentEvent>(sqsMessage.Message)
          when (eventType) {
            "BED_ASSIGNMENT_HISTORY-INSERTED" -> syncService.create(event)
            "BED_ASSIGNMENT_HISTORY-UPDATED" -> syncService.update(event)
            "BED_ASSIGNMENT_HISTORY-DELETED" -> syncService.delete(event)

            else -> log.info("Received an unexpected bed-assignment event {}", eventType)
          }
        } else {
          log.info("Feature switch is disabled for bedassignment event {}", eventType)
        }
      }

      RETRY_SYNCHRONISATION_MAPPING.name -> syncService.retryCreateMapping(jsonMapper.readValue(sqsMessage.Message))
      else -> log.info("Received an unexpected bed-assignment message type {}", sqsMessage.Type)
    }
  }
}

data class BedAssignmentEvent(
  val eventType: String,
  val eventDatetime: LocalDateTime,
  val offenderIdDisplay: String,
  val bookingId: Long,
  val bedAssignmentSeq: Int,
  override val auditModuleName: String? = null,
) : EventAudited
