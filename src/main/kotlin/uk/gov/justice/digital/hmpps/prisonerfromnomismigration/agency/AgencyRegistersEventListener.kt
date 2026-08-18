package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.AGENCY_REGISTERS_SYNC_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class AgencyRegistersEventListener(
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val agencyRegistersSynchronisationService: AgencyRegistersSynchronisationService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(AGENCY_REGISTERS_SYNC_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "agencyregisters")) {
            when (eventType) {
              "AGENCY_REGISTERS_UPDATED" -> agencyRegistersSynchronisationService.agencyUpdated(sqsMessage.Message.fromJson())
              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }

        else -> log.info("Received a message I wasn't expecting {}", sqsMessage.Type)
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

data class AgencyEvent(
  val agencyLocationId: String,
  override val auditModuleName: String,
) : EventAudited
