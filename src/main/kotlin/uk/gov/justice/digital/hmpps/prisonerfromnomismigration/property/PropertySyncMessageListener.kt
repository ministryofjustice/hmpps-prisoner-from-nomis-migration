package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PROPERTY_SYNC_QUEUE_ID
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CompletableFuture

@Service
class PropertyEventListener(
  private val propertySyncService: PropertySyncService,
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(PROPERTY_SYNC_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received property event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "property")) {
            val messageId = UUID.fromString(sqsMessage.MessageId)
            when (eventType) {
              "PRISONER_PROPERTY-INSERTED" -> propertySyncService.created(sqsMessage.Message.fromJson())
              "PRISONER_PROPERTY-UPDATED" -> propertySyncService.updated(sqsMessage.Message.fromJson())
              "PRISONER_PROPERTY-DELETED" -> propertySyncService.deleted(sqsMessage.Message.fromJson())

              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }

        RETRY_SYNCHRONISATION_MAPPING.name -> propertySyncService.retryCreateMapping(sqsMessage.Message.fromJson())
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

data class PropertyEvent(
  val eventType: String?,
  val eventDatetime: LocalDateTime?,
  val bookingId: Long?,
  val offenderIdDisplay: String?,
  val propertyContainerId: Long,
  val auditModuleName: String?,
)
