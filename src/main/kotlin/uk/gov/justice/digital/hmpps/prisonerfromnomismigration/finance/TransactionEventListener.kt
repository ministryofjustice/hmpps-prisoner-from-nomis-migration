package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.PERFORM_TRANSACTION_SYNC
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.FINANCE_SYNC_QUEUE_ID
import java.util.UUID
import java.util.concurrent.CompletableFuture

@Service
class TransactionEventListener(
  private val transactionSynchronisationService: TransactionSynchronisationService,
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(FINANCE_SYNC_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received transaction event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "transactions")) {
            val messageId = UUID.fromString(sqsMessage.MessageId)
            when (eventType) {
              // NOTE: Several inserts per second!
              "OFFENDER_TRANSACTIONS-INSERTED" -> transactionSynchronisationService.transactionInserted(
                sqsMessage.Message.fromJson(),
                messageId,
              )
              "OFFENDER_TRANSACTIONS-UPDATED" -> transactionSynchronisationService.transactionUpdated(
                sqsMessage.Message.fromJson(),
                messageId,
              )
              // extremely rare (only happened 61 times ever according to oms_deleted_rows, mostly by scripts)
              // TODO Waiting for DPS to decide whether to intercept this event
              "OFFENDER_TRANSACTIONS-DELETED" -> log.info("Received an OFFENDER_TRANSACTIONS-DELETED I wasn't expecting {}", eventType)

              "GL_TRANSACTIONS-INSERTED" -> transactionSynchronisationService.glTransactionInserted(
                sqsMessage.Message.fromJson(),
                messageId,
              )
              "GL_TRANSACTIONS-UPDATED" -> transactionSynchronisationService.glTransactionUpdated(
                sqsMessage.Message.fromJson(),
                messageId,
              )
              // extremely rare (only happened once at 11-AUG-2021 10:39:26.470007000 according to oms_deleted_rows, 8 deleted)
              // TODO Waiting for DPS to decide whether to intercept this event
              "GL_TRANSACTIONS-DELETED" -> log.info("Received a GL_TRANSACTIONS-DELETED I wasn't expecting {}", eventType)

              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }

        RETRY_SYNCHRONISATION_MAPPING.name -> transactionSynchronisationService.retryCreateTransactionMapping(sqsMessage.Message.fromJson())

        PERFORM_TRANSACTION_SYNC.name -> transactionSynchronisationService.transactionUpserted(sqsMessage.Message.fromJson())
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)
}

data class TransactionEvent(
  val transactionId: Long,
  val entrySequence: Int,
  val caseload: String,
  val transactionType: String? = null,
  val offenderIdDisplay: String,
  val bookingId: Long? = null,
  override val auditModuleName: String? = null,
) : EventAudited

data class GLTransactionEvent(
  val transactionId: Long,
  val entrySequence: Int,
  val gLEntrySequence: Int,
  val caseload: String,
  val transactionType: String? = null,
  val offenderIdDisplay: String,
  val bookingId: Long? = null,
  override val auditModuleName: String? = null,
) : EventAudited
