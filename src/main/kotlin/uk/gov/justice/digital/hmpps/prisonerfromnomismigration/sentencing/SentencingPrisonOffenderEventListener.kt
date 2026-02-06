package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import java.util.concurrent.CompletableFuture

@Service
class SentencingPrisonOffenderEventListener(
  private val sentencingAdjustmentsSynchronisationService: SentencingAdjustmentsSynchronisationService,
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventsentencing", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "sentencing")) {
            when (eventType) {
              "SENTENCE_ADJUSTMENT_UPSERTED" -> sentencingAdjustmentsSynchronisationService.synchroniseSentenceAdjustmentCreateOrUpdate(
                (sqsMessage.Message.fromJson()),
              )

              "SENTENCE_ADJUSTMENT_DELETED" -> sentencingAdjustmentsSynchronisationService.synchroniseSentenceAdjustmentDelete(
                (sqsMessage.Message.fromJson()),
              )

              "KEY_DATE_ADJUSTMENT_UPSERTED" -> sentencingAdjustmentsSynchronisationService.synchroniseKeyDateAdjustmentCreateOrUpdate(
                (sqsMessage.Message.fromJson()),
              )

              "KEY_DATE_ADJUSTMENT_DELETED" -> sentencingAdjustmentsSynchronisationService.synchroniseKeyDateAdjustmentDelete(
                (sqsMessage.Message.fromJson()),
              )

              "BOOKING_NUMBER-CHANGED" -> sentencingAdjustmentsSynchronisationService.synchronisePrisonerMerge(
                (sqsMessage.Message.fromJson()),
              )

              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }

        SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING.name -> sentencingAdjustmentsSynchronisationService.retryCreateSentenceAdjustmentMapping(
          sqsMessage.Message.fromJson(),
        )
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

data class SentenceAdjustmentOffenderEvent(
  val offenderIdDisplay: String,
  val bookingId: Long,
  val sentenceSeq: Long,
  val adjustmentId: Long,
  override val auditModuleName: String?,
) : EventAudited

data class KeyDateAdjustmentOffenderEvent(
  val offenderIdDisplay: String,
  val bookingId: Long,
  val adjustmentId: Long,
  override val auditModuleName: String?,
) : EventAudited
