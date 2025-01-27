package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.LOCATIONS_SYNC_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class LocationsPrisonOffenderEventListener(
  private val locationsSynchronisationService: LocationsSynchronisationService,
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(LOCATIONS_SYNC_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType)) {
            when (eventType) {
              "AGENCY_INTERNAL_LOCATIONS-UPDATED" -> locationsSynchronisationService.synchroniseLocation(sqsMessage.Message.fromJson())
              "AGY_INT_LOC_PROFILES-UPDATED" -> locationsSynchronisationService.synchroniseAttribute(sqsMessage.Message.fromJson())
              "INT_LOC_USAGE_LOCATIONS-UPDATED" -> locationsSynchronisationService.synchroniseUsage(sqsMessage.Message.fromJson())

              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }

        RETRY_SYNCHRONISATION_MAPPING.name -> locationsSynchronisationService.retryCreateLocationMapping(
          sqsMessage.Message.fromJson(),
        )
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)
}

data class LocationsOffenderEvent(
  val internalLocationId: Long,
  val description: String?,
  val oldDescription: String?,
  val prisonId: String?,
  val auditModuleName: String?,
  val recordDeleted: Boolean?,
)
