package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentivesSynchronisationService

private const val NOMIS_IEP_UI_SCREEN = "OIDOIEPS"

@Service
class PrisonOffenderEventListener(
  private val objectMapper: ObjectMapper,
  private val incentivesSynchronisationService: IncentivesSynchronisationService,
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
        val (bookingId, iepSeq, auditModuleName) = objectMapper.readValue<IncentiveUpsertedOffenderEvent>(sqsMessage.Message)
        log.debug("received IEP_UPSERTED Offender event for bookingId $bookingId and seq $iepSeq with auditModuleName $auditModuleName")
        if (shouldSynchronise(auditModuleName)) {
          runBlocking { incentivesSynchronisationService.synchroniseIncentive(objectMapper.readValue(sqsMessage.Message)) }
        }
      }
      else -> log.info("Received a message I wasn't expecting {}", eventType)
    } else {
      log.info("Feature switch is disabled for event {}", eventType)
    }
  }

  private fun shouldSynchronise(auditModuleName: String): Boolean {
    return auditModuleName == NOMIS_IEP_UI_SCREEN
  }

  data class SQSMessage(val Message: String, val MessageId: String, val MessageAttributes: MessageAttributes)
  data class MessageAttributes(val eventType: EventType)
  data class EventType(val Value: String, val Type: String)
}

data class IncentiveUpsertedOffenderEvent(val bookingId: Long, val iepSeq: Long, val auditModuleName: String)
