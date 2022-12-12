package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentivesSynchronisationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitSynchronisationService

private const val NOMIS_IEP_UI_SCREEN = "OIDOIEPS"

@Service
class PrisonOffenderEventListener(
  private val objectMapper: ObjectMapper,
  private val incentivesSynchronisationService: IncentivesSynchronisationService,
  private val visitSynchronisationService: VisitSynchronisationService,
  private val eventFeatureSwitch: EventFeatureSwitch
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "event", containerFactory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String) {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    val eventType = sqsMessage.MessageAttributes.eventType.Value
    if (eventFeatureSwitch.isEnabled(eventType)) when (eventType) {
      "IEP_UPSERTED" -> {
        val (offenderIdDisplay, bookingId, iepSeq, auditModuleName) = objectMapper.readValue<IncentiveUpsertedOffenderEvent>(
          sqsMessage.Message
        )
        log.debug("received IEP_UPSERTED Offender event for $offenderIdDisplay bookingId $bookingId and seq $iepSeq with auditModuleName $auditModuleName")
        if (shouldSynchronise(auditModuleName)) {
          runBlocking { incentivesSynchronisationService.synchroniseIncentive(objectMapper.readValue(sqsMessage.Message)) }
        }
      }

      "IEP_DELETED" -> {
        val (offenderIdDisplay, bookingId, iepSeq) = objectMapper.readValue<IncentiveDeletedOffenderEvent>(sqsMessage.Message)
        log.debug("received IEP_DELETED Offender event for $offenderIdDisplay bookingId $bookingId and seq $iepSeq")
        runBlocking { incentivesSynchronisationService.synchroniseDeletedIncentive(objectMapper.readValue(sqsMessage.Message)) }
      }

      "VISIT_CANCELLED" -> {
        val (offenderIdDisplay, visitId, auditModuleName) = objectMapper.readValue<VisitCancelledOffenderEvent>(
          sqsMessage.Message
        )
        log.debug("received VISIT_CANCELLED Offender event for offenderNo $offenderIdDisplay and visitId $visitId with auditModuleName $auditModuleName")
        runBlocking { visitSynchronisationService.cancelVisit(objectMapper.readValue(sqsMessage.Message)) }
      }

      else -> log.info("Received a message I wasn't expecting {}", eventType)
    } else {
      log.info("Feature switch is disabled for event {}", eventType)
    }
  }

  private fun shouldSynchronise(auditModuleName: String?): Boolean {
    return auditModuleName == NOMIS_IEP_UI_SCREEN
  }

  data class SQSMessage(val Message: String, val MessageId: String, val MessageAttributes: MessageAttributes)
  data class MessageAttributes(val eventType: EventType)
  data class EventType(val Value: String, val Type: String)
}

data class IncentiveUpsertedOffenderEvent(
  val offenderIdDisplay: String,
  val bookingId: Long,
  val iepSeq: Long,
  val auditModuleName: String?
)

data class IncentiveDeletedOffenderEvent(val offenderIdDisplay: String, val bookingId: Long, val iepSeq: Long)
data class VisitCancelledOffenderEvent(val offenderIdDisplay: String, val visitId: Long, val auditModuleName: String)
