package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.physicalattributes.PhysicalAttributesSyncService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson.profiledetails.ProfileDetailsSyncService
import java.util.concurrent.CompletableFuture

@Service
class PrisonPersonEventListener(
  private val physicalAttributesSyncService: PhysicalAttributesSyncService,
  private val profileDetailsSyncService: ProfileDetailsSyncService,
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventprisonperson", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "prisonperson")) {
            when (eventType) {
              "OFFENDER_PHYSICAL_ATTRIBUTES-CHANGED" -> physicalAttributesSyncService.physicalAttributesChanged(sqsMessage.Message.fromJson())
              "OFFENDER_PHYSICAL_DETAILS-CHANGED" -> profileDetailsSyncService.profileDetailsChanged(sqsMessage.Message.fromJson())
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

data class PhysicalAttributesChangedEvent(
  val offenderIdDisplay: String,
  val bookingId: Long,
)

data class ProfileDetailsChangedEvent(
  val offenderIdDisplay: String,
  val bookingId: Long,
  val profileType: String,
)
