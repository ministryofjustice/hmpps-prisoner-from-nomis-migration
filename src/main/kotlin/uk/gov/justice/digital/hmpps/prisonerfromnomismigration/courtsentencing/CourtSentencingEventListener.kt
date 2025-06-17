package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_COURT_APPEARANCE_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_COURT_CASE_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_COURT_CHARGE_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_PRISONER_MERGE_COURT_CASE_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_SENTENCE_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_SENTENCE_TERM_SYNCHRONISATION_MAPPING
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

@Service
class CourtSentencingEventListener(
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val courtSentencingSynchronisationService: CourtSentencingSynchronisationService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventcourtsentencing", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "courtsentencing")) {
            when (eventType) {
              "OFFENDER_CASES-INSERTED" -> courtSentencingSynchronisationService.nomisCourtCaseInserted(sqsMessage.Message.fromJson())
              "OFFENDER_CASES-UPDATED" -> courtSentencingSynchronisationService.nomisCourtCaseUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_CASES-DELETED" -> courtSentencingSynchronisationService.nomisCourtCaseDeleted(sqsMessage.Message.fromJson())
              "COURT_EVENTS-INSERTED" -> courtSentencingSynchronisationService.nomisCourtAppearanceInserted(sqsMessage.Message.fromJson())
              "OFFENDER_CASES-LINKED" -> courtSentencingSynchronisationService.nomisCourtCaseLinked(sqsMessage.Message.fromJson())
              "OFFENDER_CASES-UNLINKED" -> courtSentencingSynchronisationService.nomisCourtCaseUnlinked(sqsMessage.Message.fromJson())
              "COURT_EVENTS-UPDATED" -> courtSentencingSynchronisationService.nomisCourtAppearanceUpdated(sqsMessage.Message.fromJson())
              "COURT_EVENTS-DELETED" -> courtSentencingSynchronisationService.nomisCourtAppearanceDeleted(sqsMessage.Message.fromJson())
              "COURT_EVENT_CHARGES-INSERTED" -> courtSentencingSynchronisationService.nomisCourtChargeInserted(sqsMessage.Message.fromJson())
              "COURT_EVENT_CHARGES-DELETED" -> courtSentencingSynchronisationService.nomisCourtChargeDeleted(sqsMessage.Message.fromJson())
              "COURT_EVENT_CHARGES-UPDATED" -> courtSentencingSynchronisationService.nomisCourtChargeUpdated(sqsMessage.Message.fromJson())
              "COURT_EVENT_CHARGES-LINKED" -> courtSentencingSynchronisationService.nomisCourtChargeLinked(sqsMessage.Message.fromJson())
              "OFFENDER_CHARGES-UPDATED" -> courtSentencingSynchronisationService.nomisOffenderChargeUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_SENTENCES-INSERTED" -> courtSentencingSynchronisationService.nomisSentenceInserted(sqsMessage.Message.fromJson())
              "OFFENDER_SENTENCES-DELETED" -> courtSentencingSynchronisationService.nomisSentenceDeleted(sqsMessage.Message.fromJson())
              "OFFENDER_SENTENCES-UPDATED" -> courtSentencingSynchronisationService.nomisSentenceUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_SENTENCE_TERMS-INSERTED" -> courtSentencingSynchronisationService.nomisSentenceTermInserted(sqsMessage.Message.fromJson())
              "OFFENDER_SENTENCE_TERMS-DELETED" -> courtSentencingSynchronisationService.nomisSentenceTermDeleted(sqsMessage.Message.fromJson())
              "OFFENDER_SENTENCE_TERMS-UPDATED" -> courtSentencingSynchronisationService.nomisSentenceTermUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_CASE_IDENTIFIERS-DELETED",
              "OFFENDER_CASE_IDENTIFIERS-INSERTED",
              "OFFENDER_CASE_IDENTIFIERS-UPDATED",
              -> courtSentencingSynchronisationService.nomisCaseIdentifiersUpdated(eventType, sqsMessage.Message.fromJson())
              "prison-offender-events.prisoner.merged" -> courtSentencingSynchronisationService.prisonerMerged(sqsMessage.Message.fromJson())
              "OFFENDER_FIXED_TERM_RECALLS-UPDATED" -> courtSentencingSynchronisationService.nomisRecallReturnToCustodyDataChanged(sqsMessage.Message.fromJson())
              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }

        RETRY_COURT_CASE_SYNCHRONISATION_MAPPING ->
          courtSentencingSynchronisationService.retryCreateCourtCaseMapping(
            sqsMessage.Message.fromJson(),
          )

        RETRY_COURT_APPEARANCE_SYNCHRONISATION_MAPPING ->
          courtSentencingSynchronisationService.retryCreateCourtAppearanceMapping(
            sqsMessage.Message.fromJson(),
          )

        RETRY_COURT_CHARGE_SYNCHRONISATION_MAPPING ->
          courtSentencingSynchronisationService.retryCreateCourtChargeMapping(
            sqsMessage.Message.fromJson(),
          )

        RETRY_SENTENCE_SYNCHRONISATION_MAPPING ->
          courtSentencingSynchronisationService.retryCreateSentenceMapping(
            sqsMessage.Message.fromJson(),
          )

        RETRY_SENTENCE_TERM_SYNCHRONISATION_MAPPING ->
          courtSentencingSynchronisationService.retryCreateSentenceTermMapping(
            sqsMessage.Message.fromJson(),
          )

        RETRY_PRISONER_MERGE_COURT_CASE_SYNCHRONISATION_MAPPING ->
          courtSentencingSynchronisationService.retryCreatePrisonerMergeCourtCaseMapping(
            sqsMessage.Message.fromJson(),
          )
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)
}

data class CourtCaseEvent(
  val caseId: Long,
  val offenderIdDisplay: String,
  val bookingId: Long,
  val auditModuleName: String?,
)

data class CourtCaseLinkingEvent(
  val caseId: Long,
  val combinedCaseId: Long,
  val offenderIdDisplay: String,
  val bookingId: Long,
  val auditModuleName: String?,
)

data class CourtAppearanceEvent(
  val eventId: Long,
  val caseId: Long?,
  val offenderIdDisplay: String,
  val bookingId: Long,
  val auditModuleName: String?,
)

data class CourtEventChargeEvent(
  val eventId: Long,
  val chargeId: Long,
  val offenderIdDisplay: String,
  val bookingId: Long,
  val auditModuleName: String?,
)

data class CourtEventChargeLinkingEvent(
  val eventId: Long,
  val chargeId: Long,
  val combinedCaseId: Long,
  val caseId: Long,
  val offenderIdDisplay: String,
  val bookingId: Long,
  val auditModuleName: String?,
  val eventDatetime: LocalDateTime,
)

data class OffenderChargeEvent(
  val chargeId: Long,
  val offenderIdDisplay: String,
  val bookingId: Long,
  val auditModuleName: String?,
  val offenceCodeChange: Boolean,
)

data class OffenderSentenceEvent(
  val sentenceSeq: Int,
  val sentenceLevel: String,
  val sentenceCategory: String,
  val caseId: Long?,
  val offenderIdDisplay: String,
  val bookingId: Long,
  val auditModuleName: String?,
)

data class OffenderSentenceTermEvent(
  val termSequence: Int,
  val sentenceSeq: Int,
  val offenderIdDisplay: String,
  val bookingId: Long,
  val auditModuleName: String?,
)

data class CaseIdentifiersEvent(
  val caseId: Long,
  val identifierType: String,
  val identifierNo: String,
  val bookingId: Long,
  val auditModuleName: String?,
)

data class ReturnToCustodyDateEvent(
  val offenderIdDisplay: String,
  val bookingId: Long,
  val auditModuleName: String,
  val eventType: String,
)
