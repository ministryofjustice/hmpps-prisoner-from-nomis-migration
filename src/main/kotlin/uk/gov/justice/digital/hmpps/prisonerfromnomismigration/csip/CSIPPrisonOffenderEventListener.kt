package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_CHILD_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CSIP_SYNC_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class CSIPPrisonOffenderEventListener(
  private val csipSynchronisationService: CSIPSynchronisationService,
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(CSIP_SYNC_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "syscon-devs-prisoner_from_nomis_csip_queue", kind = SpanKind.SERVER)
  fun onMessage(message: String): CompletableFuture<Void> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "csip")) {
            when (eventType) {
              // The first grouping will all (eventually) call the same method
              "CSIP_REPORTS-INSERTED" -> csipSynchronisationService.csipReportInserted(sqsMessage.Message.fromJson())
              "CSIP_REPORTS-UPDATED" -> csipSynchronisationService.csipReportUpdated(sqsMessage.Message.fromJson())
              "CSIP_REPORTS-DELETED" -> csipSynchronisationService.csipReportDeleted(sqsMessage.Message.fromJson())
              "CSIP_PLANS-INSERTED" -> log.debug("Insert CSIP Plan")
              "CSIP_PLANS-UPDATED" -> log.debug("Update CSIP Plan")
              "CSIP_REVIEWS-INSERTED" -> log.debug("Insert CSIP Review")
              "CSIP_REVIEWS-UPDATED" -> log.debug("Update CSIP Review")
              "CSIP_ATTENDEES-INSERTED" -> log.debug("Insert CSIP Attendee")
              "CSIP_ATTENDEES-UPDATED" -> log.debug("Update CSIP Attendee")
              "CSIP_FACTORS-INSERTED",
              "CSIP_FACTORS-UPDATED",
              -> csipSynchronisationService.csipFactorUpserted(sqsMessage.Message.fromJson())
              "CSIP_INTVW-INSERTED" -> log.debug("Insert CSIP Interview")
              "CSIP_INTVW-UPDATED" -> log.debug("Update CSIP Interview")

              // TODO check if needed
              // "prison-offender-events.prisoner.merged"

              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }

        RETRY_SYNCHRONISATION_MAPPING.name ->
          csipSynchronisationService.retryCreateCSIPReportMapping(sqsMessage.Message.fromJson())

        RETRY_SYNCHRONISATION_CHILD_MAPPING.name ->
          csipSynchronisationService.retryUpdateCSIPReportMapping(sqsMessage.Message.fromJson())
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

// Depending on the type of update/delete there will be additional params - but we don't care
data class CSIPReportEvent(
  val csipReportId: Long,
  val offenderIdDisplay: String,
  val auditModuleName: String?,
)

data class CSIPFactorEvent(
  val csipFactorId: Long,
  val csipReportId: Long,
  val offenderIdDisplay: String,
  val auditModuleName: String?,
)

data class CSIPPlanEvent(
  val csipPlanId: Long,
  val csipReportId: Long,
  val offenderIdDisplay: String,
  val auditModuleName: String?,
)

data class CSIPInterviewEvent(
  val csipInterviewId: Long,
  val csipReportId: Long,
  val offenderIdDisplay: String,
  val auditModuleName: String?,
)

data class CSIPAttendeeEvent(
  val csipAttendeeId: Long,
  val csipReportId: Long,
  val offenderIdDisplay: String,
  val auditModuleName: String?,
)

private fun asCompletableFuture(
  process: suspend () -> Unit,
): CompletableFuture<Void> = CoroutineScope(Dispatchers.Default).future {
  process()
}.thenAccept { }
