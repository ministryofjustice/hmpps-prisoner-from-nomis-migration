package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import com.fasterxml.jackson.core.type.TypeReference
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
import java.util.concurrent.CompletableFuture

private const val NOMIS_IEP_UI_SCREEN = "OIDOIEPS"

@Service
class IncentivesPrisonOffenderEventListener(
  private val objectMapper: ObjectMapper,
  private val incentivesSynchronisationService: IncentivesSynchronisationService,
  private val eventFeatureSwitch: EventFeatureSwitch
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventincentives", factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "Digital-Prison-Services-prisoner_from_nomis_iep_queue", kind = SpanKind.SERVER)
  fun onMessage(message: String): CompletableFuture<Void> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return CoroutineScope(Dispatchers.Default).future {
      when (sqsMessage.Type) {

        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType)) when (eventType) {
            "IEP_UPSERTED" -> {
              val (offenderIdDisplay, bookingId, iepSeq, auditModuleName) = objectMapper.readValue<IncentiveUpsertedOffenderEvent>(
                sqsMessage.Message
              )
              log.debug("received IEP_UPSERTED Offender event for $offenderIdDisplay bookingId $bookingId and seq $iepSeq with auditModuleName $auditModuleName")
              if (shouldSynchronise(auditModuleName)) {
                incentivesSynchronisationService.synchroniseIncentive(objectMapper.readValue(sqsMessage.Message))
              }
            }

            "IEP_DELETED" -> {
              val (offenderIdDisplay, bookingId, iepSeq) = objectMapper.readValue<IncentiveDeletedOffenderEvent>(
                sqsMessage.Message
              )
              log.debug("received IEP_DELETED Offender event for $offenderIdDisplay bookingId $bookingId and seq $iepSeq")
              incentivesSynchronisationService.synchroniseDeletedIncentive(objectMapper.readValue(sqsMessage.Message))
            }

            else -> log.info("Received a message I wasn't expecting: {}", eventType)
          } else {
            log.warn("Feature switch is disabled for {}", eventType)
          }
        }

        IncentiveMessages.SYNCHRONISE_CURRENT_INCENTIVE.name -> incentivesSynchronisationService.handleSynchroniseCurrentIncentiveMessage(
          sqsMessage.Message.fromJson()
        )

        IncentiveMessages.RETRY_INCENTIVE_SYNCHRONISATION_MAPPING.name -> incentivesSynchronisationService.retryCreateIncentiveMapping(
          sqsMessage.Message.fromJson()
        )
      }
    }.thenAccept { }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this, object : TypeReference<T>() {})

  private fun shouldSynchronise(auditModuleName: String?): Boolean {
    return auditModuleName == NOMIS_IEP_UI_SCREEN
  }
}

data class IncentiveUpsertedOffenderEvent(
  val offenderIdDisplay: String,
  val bookingId: Long,
  val iepSeq: Long,
  val auditModuleName: String?
)

data class IncentiveDeletedOffenderEvent(val offenderIdDisplay: String, val bookingId: Long, val iepSeq: Long)

data class IncentiveBooking(
  val nomisBookingId: Long
)
