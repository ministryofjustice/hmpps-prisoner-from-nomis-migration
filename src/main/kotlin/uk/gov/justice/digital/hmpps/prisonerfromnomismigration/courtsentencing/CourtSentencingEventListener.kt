package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.SentenceId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingAdjustmentsSynchronisationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CASE_BOOKING_RESYNCHRONISATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CASE_RESYNCHRONISATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RECALL_BREACH_COURT_EVENT_CHARGE_INSERTED
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RECALL_SENTENCE_ADJUSTMENTS_SYNCHRONISATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_COURT_APPEARANCE_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_COURT_CASE_BOOKING_CLONE_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_COURT_CASE_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_COURT_CHARGE_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_PRISONER_MERGE_COURT_CASE_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_SENTENCE_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_SENTENCE_TERM_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SENTENCE_RESYNCHRONISATION
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

@Service
class CourtSentencingEventListener(
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val courtSentencingSynchronisationService: CourtSentencingSynchronisationService,
  private val sentencingAdjustmentsSynchronisationService: SentencingAdjustmentsSynchronisationService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventcourtsentencing", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "courtsentencing")) {
            when (eventType) {
              "OFFENDER_CASES-INSERTED" -> courtSentencingSynchronisationService.nomisCourtCaseInserted(sqsMessage.Message.fromJson())
              "OFFENDER_CASES-UPDATED" -> courtSentencingSynchronisationService.nomisCourtCaseUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_CASES-DELETED" -> courtSentencingSynchronisationService.nomisCourtCaseDeleted(sqsMessage.Message.fromJson())
              "OFFENDER_CASES-LINKED" -> courtSentencingSynchronisationService.nomisCourtCaseLinked(sqsMessage.Message.fromJson())
              "OFFENDER_CASES-UNLINKED" -> courtSentencingSynchronisationService.nomisCourtCaseUnlinked(sqsMessage.Message.fromJson())
              "COURT_EVENTS-INSERTED" -> courtSentencingSynchronisationService.nomisCourtAppearanceInserted(sqsMessage.Message.fromJson())
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
              "OFFENDER_SENTENCE_CHARGES-DELETED" -> courtSentencingSynchronisationService.nomisSentenceChargeDeleted(sqsMessage.Message.fromJson())
              "OFFENDER_SENTENCE_CHARGES-INSERTED" -> courtSentencingSynchronisationService.nomisSentenceChargeInserted(
                sqsMessage.Message.fromJson(),
              )
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

        RECALL_BREACH_COURT_EVENT_CHARGE_INSERTED -> courtSentencingSynchronisationService.nomisRecallBeachCourtChargeInserted(
          sqsMessage.Message.fromJson(),
        )

        RECALL_SENTENCE_ADJUSTMENTS_SYNCHRONISATION -> sentencingAdjustmentsSynchronisationService.nomisSentenceAdjustmentsUpdate(
          sqsMessage.Message.fromJson(),
        )

        SENTENCE_RESYNCHRONISATION -> courtSentencingSynchronisationService.nomisSentenceResynchronisation(
          sqsMessage.Message.fromJson(),
        )

        CASE_RESYNCHRONISATION -> courtSentencingSynchronisationService.nomisCaseResynchronisation(
          sqsMessage.Message.fromJson(),
        )
        CASE_BOOKING_RESYNCHRONISATION ->
          courtSentencingSynchronisationService.nomisCaseBookingMoveResynchronisation(sqsMessage.Message.fromJson())

        RETRY_COURT_CASE_BOOKING_CLONE_SYNCHRONISATION_MAPPING ->
          courtSentencingSynchronisationService.retryCreateCaseBookingCloneMapping(
            sqsMessage.Message.fromJson(),
          )
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

data class CourtCaseEvent(
  val caseId: Long,
  val offenderIdDisplay: String,
  val bookingId: Long,
  override val auditModuleName: String?,
) : EventAudited

data class CourtCaseLinkingEvent(
  val caseId: Long,
  val combinedCaseId: Long,
  val offenderIdDisplay: String,
  val bookingId: Long,
  override val auditModuleName: String?,
) : EventAudited

data class CourtAppearanceEvent(
  val eventId: Long,
  val caseId: Long?,
  val offenderIdDisplay: String,
  val bookingId: Long,
  val isBreachHearing: Boolean,
  override val auditModuleName: String?,
) : EventAudited

data class CourtEventChargeEvent(
  val eventId: Long,
  val chargeId: Long,
  val offenderIdDisplay: String,
  val bookingId: Long,
  override val auditModuleName: String?,
) : EventAudited

data class RecallBreachCourtEventCharge(
  val eventId: Long,
  val chargeId: Long,
  val offenderIdDisplay: String,
  val bookingId: Long,
)

data class CourtEventChargeLinkingEvent(
  val eventId: Long,
  val chargeId: Long,
  val combinedCaseId: Long,
  val caseId: Long,
  val offenderIdDisplay: String,
  val bookingId: Long,
  val eventDatetime: LocalDateTime,
  override val auditModuleName: String?,
) : EventAudited

data class OffenderChargeEvent(
  val chargeId: Long,
  val offenderIdDisplay: String,
  val bookingId: Long,
  val offenceCodeChange: Boolean,
  override val auditModuleName: String?,
) : EventAudited

data class OffenderSentenceEvent(
  val sentenceSeq: Int,
  val sentenceLevel: String,
  val sentenceCategory: String,
  val caseId: Long?,
  val offenderIdDisplay: String,
  val bookingId: Long,
  override val auditModuleName: String?,
) : EventAudited

data class OffenderSentenceResynchronisationEvent(
  val sentenceSeq: Int,
  val dpsSentenceUuid: String,
  val offenderNo: String,
  val bookingId: Long,
  val caseId: Long,
  val dpsAppearanceUuid: String,
  val dpsConsecutiveSentenceUuid: String?,
)

data class OffenderSentenceChargeEvent(
  val sentenceSeq: Int,
  val chargeId: Long,
  val offenderIdDisplay: String,
  val bookingId: Long,
  override val auditModuleName: String?,
) : EventAudited

data class OffenderCaseResynchronisationEvent(
  val dpsCaseUuid: String,
  val offenderNo: String,
  val caseId: Long,
)

data class OffenderCaseBookingResynchronisationEvent(
  val offenderNo: String,
  val fromBookingId: Long = 0,
  val toBookingId: Long = 0,
  val caseIds: List<Long>,
  val casesMoved: List<CaseBookingChanged> = emptyList(),
)

data class CaseBookingChanged(
  val caseId: Long,
  val sentences: List<SentenceBookingChanged>,
)

data class SentenceBookingChanged(
  val sentenceSequence: Int,
)

data class OffenderSentenceTermEvent(
  val termSequence: Int,
  val sentenceSeq: Int,
  val offenderIdDisplay: String,
  val bookingId: Long,
  override val auditModuleName: String?,
) : EventAudited

data class CaseIdentifiersEvent(
  val caseId: Long,
  val identifierType: String,
  val identifierNo: String,
  override val auditModuleName: String?,
) : EventAudited

data class ReturnToCustodyDateEvent(
  val offenderIdDisplay: String,
  val bookingId: Long,
  val eventType: String,
  override val auditModuleName: String?,
) : EventAudited

data class SentenceIdAndAdjustmentType(
  val sentenceId: SentenceId,
  val adjustmentIds: List<Long>,
)

data class SyncSentenceAdjustment(
  val offenderNo: String,
  val sentences: List<SentenceIdAndAdjustmentType>,
)
