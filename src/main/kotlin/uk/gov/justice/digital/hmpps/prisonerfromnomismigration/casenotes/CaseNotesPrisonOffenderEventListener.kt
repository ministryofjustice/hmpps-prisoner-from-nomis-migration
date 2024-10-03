package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CASENOTES_SYNC_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class CaseNotesPrisonOffenderEventListener(
  private val caseNotesSynchronisationService: CaseNotesSynchronisationService,
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(CASENOTES_SYNC_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "casenotes")) {
            when (eventType) {
              "OFFENDER_CASE_NOTES-INSERTED" -> caseNotesSynchronisationService.caseNoteInserted(sqsMessage.Message.fromJson())
              "OFFENDER_CASE_NOTES-UPDATED" -> caseNotesSynchronisationService.caseNoteUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_CASE_NOTES-DELETED" -> caseNotesSynchronisationService.caseNoteDeleted(sqsMessage.Message.fromJson())
              // There are about 2 deletions per day
              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }

        RETRY_SYNCHRONISATION_MAPPING.name -> caseNotesSynchronisationService.retryCreateCaseNoteMapping(
          sqsMessage.Message.fromJson(),
        )
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

data class CaseNotesEvent(
  val caseNoteId: Long,
  val caseNoteType: String?,
  val caseNoteSubType: String?,
  val offenderIdDisplay: String,
  val bookingId: Long?,
  val auditModuleName: String?,
  val recordDeleted: Boolean?,
)

private fun asCompletableFuture(
  process: suspend () -> Unit,
): CompletableFuture<Void> = CoroutineScope(Dispatchers.Default).future {
  process()
}.thenAccept { }
