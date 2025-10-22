package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

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
import java.util.concurrent.CompletableFuture

@Service
class OfficialVisitsEventListener(
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val service: OfficialVisitsSynchronisationService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventofficialvisits", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "officialvisits")) {
            when (eventType) {
              "OFFENDER_OFFICIAL_VISIT-INSERTED" -> service.visitAdded(sqsMessage.Message.fromJson())
              "OFFENDER_OFFICIAL_VISIT-UPDATED" -> service.visitUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_OFFICIAL_VISIT-DELETED" -> service.visitDeleted(sqsMessage.Message.fromJson())
              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }
      }
    }
  }
  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)
}

data class VisitEvent(
  val visitId: Long,
  val bookingId: Long,
  val offenderIdDisplay: String,
  val agencyLocationId: String,
  override val auditModuleName: String,
) : EventAudited
