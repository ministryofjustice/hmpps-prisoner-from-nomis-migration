package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VISIT_BALANCE_SYNC_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class VisitBalanceEventListener(
  private val service: VisitBalanceSynchronisationService,
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(VISIT_BALANCE_SYNC_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "visitbalance")) {
            when (eventType) {
              // Note. We only receive comment changes in UPDATED events (so dps are not interested in this event)
              "OFFENDER_VISIT_BALANCE_ADJS-INSERTED" -> service.visitBalanceAdjustmentInserted(sqsMessage.Message.fromJson())
              "OFFENDER_VISIT_BALANCE_ADJS-DELETED" -> service.visitBalanceAdjustmentDeleted(sqsMessage.Message.fromJson())

              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }
        RETRY_SYNCHRONISATION_MAPPING.name -> service.retryCreateMapping(sqsMessage.Message.fromJson())
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

data class VisitBalanceOffenderEvent(
  val visitBalanceAdjustmentId: Long,
  val offenderIdDisplay: String,
  val bookingId: Long,
  override val auditModuleName: String?,
) : EventAudited
