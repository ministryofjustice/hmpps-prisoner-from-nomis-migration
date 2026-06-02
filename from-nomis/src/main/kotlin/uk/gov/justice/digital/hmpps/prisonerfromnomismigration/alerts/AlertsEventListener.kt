package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RESYNCHRONISE_MOVE_BOOKING_TARGET
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_RESYNCHRONISATION_MAPPING_BATCH
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_RESYNCHRONISATION_MERGED_MAPPING_BATCH
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING_BATCH
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import java.util.concurrent.CompletableFuture

@Service
class AlertsEventListener(
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val alertsSynchronisationService: AlertsSynchronisationService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventalerts", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "alerts")) {
            when (eventType) {
              "ALERT-UPDATED" -> alertsSynchronisationService.nomisAlertUpdated(sqsMessage.Message.fromJson())
              "ALERT-INSERTED" -> alertsSynchronisationService.nomisAlertInserted(sqsMessage.Message.fromJson())
              "ALERT-DELETED" -> alertsSynchronisationService.nomisAlertDeleted(sqsMessage.Message.fromJson())
              "prison-offender-events.prisoner.merged" -> alertsSynchronisationService.synchronisePrisonerMerge(sqsMessage.Message.fromJson())
              "prison-offender-events.prisoner.booking.moved" -> alertsSynchronisationService.synchronisePrisonerBookingMoved(sqsMessage.Message.fromJson())
              "prisoner-offender-search.prisoner.received" -> alertsSynchronisationService.resynchronisePrisonerAlertsForAdmission(sqsMessage.Message.fromJson())

              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }

        RETRY_SYNCHRONISATION_MAPPING.name -> alertsSynchronisationService.retryCreateMapping(
          sqsMessage.Message.fromJson(),
        )

        RETRY_SYNCHRONISATION_MAPPING_BATCH.name -> alertsSynchronisationService.retryCreateMappingsBatch(
          sqsMessage.Message.fromJson(),
        )
        RETRY_RESYNCHRONISATION_MAPPING_BATCH.name -> alertsSynchronisationService.retryReplaceMappingsBatch(
          sqsMessage.Message.fromJson(),
        )
        RETRY_RESYNCHRONISATION_MERGED_MAPPING_BATCH.name -> alertsSynchronisationService.retryReplaceMergedMappingsBatch(
          sqsMessage.Message.fromJson(),
        )
        RESYNCHRONISE_MOVE_BOOKING_TARGET.name -> alertsSynchronisationService.synchronisePrisonerBookingMovedForPrisonerIfNecessary(sqsMessage.Message.fromJson())
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

data class AlertInsertedEvent(
  val bookingId: Long,
  val alertSeq: Long,
  val offenderIdDisplay: String,
)

data class AlertUpdatedEvent(
  val bookingId: Long,
  val alertSeq: Long,
  val offenderIdDisplay: String,
)
