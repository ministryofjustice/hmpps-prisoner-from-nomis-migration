package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentivesSynchronisationService

@Service
class PrisonOffenderEventListener(
  private val objectMapper: ObjectMapper,
  private val incentivesSynchronisationService: IncentivesSynchronisationService,
  private val telemetryClient: TelemetryClient
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "event", containerFactory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String) {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    when (val eventType = sqsMessage.MessageAttributes.eventType.Value) {
      "IEP_UPSERTED" -> {
        val (bookingId, iepSeq, auditModuleName) = objectMapper.readValue<IncentiveUpsertedOffenderEvent>(sqsMessage.Message)
        log.debug("received IEP_UPSERTED Offender event for bookingId $bookingId and seq $iepSeq with auditModuleName $auditModuleName")
        if (shouldSynchronise(auditModuleName)) {
          runBlocking { incentivesSynchronisationService.synchroniseIncentive(objectMapper.readValue(sqsMessage.Message)) }
        }
      }
      else -> log.info("Received a message I wasn't expecting {}", eventType)
    }

    /* what triggered the update/insert? (audit module)

     dont synchronise if originates from incentives service or originates from one of the stored nomis procs

        OCUWARNG         |    3704| // transfer via court??  (popup)
        PRISON_API       |    7607|
        OIDADMIS         |    8430|
        MERGE            |      76| To synchronise
        OIDOIEPS         |    9160| To synchronise
        OIDITRAN         |     971|
        OSIOSEAR         |       1|

     we ignore PRISON_API (incentives service), OIDITRAN and OIDADMIS (because incentives already handles admission and transfer events)
     */
  }

  private fun shouldSynchronise(auditModuleName: String): Boolean {
    return auditModuleName == "OIDOIEPS"
  }

  data class SQSMessage(val Message: String, val MessageId: String, val MessageAttributes: MessageAttributes)
  data class MessageAttributes(val eventType: EventType)
  data class EventType(val Value: String, val Type: String)
}

data class IncentiveUpsertedOffenderEvent(val bookingId: Long, val iepSeq: Long, val auditModuleName: String)
