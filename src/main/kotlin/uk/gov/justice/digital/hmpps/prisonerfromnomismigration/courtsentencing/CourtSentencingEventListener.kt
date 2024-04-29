package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_COURT_APPEARANCE_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_COURT_CASE_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.RETRY_COURT_CHARGE_SYNCHRONISATION_MAPPING
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
  @WithSpan(value = "syscon-devs-prisoner_from_nomis_courtsentencing_queue", kind = SpanKind.SERVER)
  fun onMessage(message: String): CompletableFuture<Void> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType)) {
            when (eventType) {
              "OFFENDER_CASES-INSERTED" -> courtSentencingSynchronisationService.nomisCourtCaseInserted(sqsMessage.Message.fromJson())
              "OFFENDER_CASES-UPDATED" -> courtSentencingSynchronisationService.nomisCourtCaseUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_CASES-DELETED" -> courtSentencingSynchronisationService.nomisCourtCaseDeleted(sqsMessage.Message.fromJson())
              "COURT_EVENTS-INSERTED" -> courtSentencingSynchronisationService.nomisCourtAppearanceInserted(sqsMessage.Message.fromJson())
              "COURT_EVENTS-UPDATED" -> courtSentencingSynchronisationService.nomisCourtAppearanceUpdated(sqsMessage.Message.fromJson())
              "COURT_EVENTS-DELETED" -> courtSentencingSynchronisationService.nomisCourtAppearanceDeleted(sqsMessage.Message.fromJson())
              "COURT_EVENT_CHARGES-INSERTED" -> courtSentencingSynchronisationService.nomisCourtChargeInserted(sqsMessage.Message.fromJson())
              "COURT_EVENT_CHARGES-DELETED" -> courtSentencingSynchronisationService.nomisCourtChargeDeleted(sqsMessage.Message.fromJson())
              "OFFENDER_CHARGES-UPDATED" -> courtSentencingSynchronisationService.nomisOffenderChargeUpdated(sqsMessage.Message.fromJson())

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
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

data class CourtCaseEvent(
  val courtCaseId: Long,
  val offenderIdDisplay: String,
  val bookingId: Long,
  val auditModuleName: String?,
)

data class CourtAppearanceEvent(
  val courtAppearanceId: Long,
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

data class OffenderChargeEvent(
  val chargeId: Long,
  val offenderIdDisplay: String,
  val bookingId: Long,
  val auditModuleName: String?,
)

private fun asCompletableFuture(
  process: suspend () -> Unit,
): CompletableFuture<Void> {
  return CoroutineScope(Dispatchers.Default).future {
    process()
  }.thenAccept { }
}
