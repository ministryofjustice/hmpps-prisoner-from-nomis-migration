package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CASENOTES_SYNC_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class CaseNotesEventListener(
  private val caseNotesSynchronisationService: CaseNotesSynchronisationService,
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(CASENOTES_SYNC_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
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
              "prison-offender-events.prisoner.merged" -> caseNotesSynchronisationService.synchronisePrisonerMerged(sqsMessage.Message.fromJson())
              "prison-offender-events.prisoner.booking.moved" -> caseNotesSynchronisationService.synchronisePrisonerBookingMoved(sqsMessage.Message.fromJson())
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

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

data class CaseNotesEvent(
  val caseNoteId: Long,
  val caseNoteType: String? = null,
  val caseNoteSubType: String? = null,
  // offenderIdDisplay not present for delete event
  val offenderIdDisplay: String? = null,
  val bookingId: Long? = null,
  val auditModuleName: String? = null,
  val recordDeleted: Boolean? = null,
)
