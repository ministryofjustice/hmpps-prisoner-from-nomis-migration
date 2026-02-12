package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

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
import java.util.concurrent.CompletableFuture

@Service
class OfficialVisitsEventListener(
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
  private val visitsService: OfficialVisitsSynchronisationService,
  private val visitSlotsService: VisitSlotsSynchronisationService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("eventofficialvisits", factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "officialvisits")) {
            when (eventType) {
              "OFFENDER_OFFICIAL_VISIT-INSERTED" -> visitsService.visitAdded(sqsMessage.Message.fromJson())
              "OFFENDER_OFFICIAL_VISIT-UPDATED" -> visitsService.visitUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_OFFICIAL_VISIT-DELETED" -> visitsService.visitDeleted(sqsMessage.Message.fromJson())
              "OFFENDER_OFFICIAL_VISIT_VISITORS-INSERTED" -> visitsService.visitorAdded(sqsMessage.Message.fromJson())
              "OFFENDER_OFFICIAL_VISIT_VISITORS-UPDATED" -> visitsService.visitorUpdated(sqsMessage.Message.fromJson())
              "OFFENDER_OFFICIAL_VISIT_VISITORS-DELETED" -> visitsService.visitorDeleted(sqsMessage.Message.fromJson())
              "AGENCY_VISIT_TIMES-INSERTED" -> visitSlotsService.visitTimeslotAdded(sqsMessage.Message.fromJson())
              "AGENCY_VISIT_TIMES-UPDATED" -> visitSlotsService.visitTimeslotUpdated(sqsMessage.Message.fromJson())
              "AGENCY_VISIT_TIMES-DELETED" -> visitSlotsService.visitTimeslotDeleted(sqsMessage.Message.fromJson())
              "AGENCY_VISIT_SLOTS-INSERTED" -> visitSlotsService.visitSlotAdded(sqsMessage.Message.fromJson())
              "AGENCY_VISIT_SLOTS-UPDATED" -> visitSlotsService.visitSlotUpdated(sqsMessage.Message.fromJson())
              "AGENCY_VISIT_SLOTS-DELETED" -> visitSlotsService.visitSlotDeleted(sqsMessage.Message.fromJson())
              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }

        else -> retryMapping(sqsMessage.Type, sqsMessage.Message)
      }
    }
  }
  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
  private suspend fun retryMapping(mappingName: String, message: String) {
    when (OfficialVisitsSynchronisationMessageType.valueOf(mappingName)) {
      OfficialVisitsSynchronisationMessageType.RETRY_SYNCHRONISATION_TIME_SLOT_MAPPING -> visitSlotsService.retryCreateVisitTimeSlotMapping(message.fromJson())
      OfficialVisitsSynchronisationMessageType.RETRY_SYNCHRONISATION_VISIT_SLOT_MAPPING -> visitSlotsService.retryCreateVisitSlotMapping(message.fromJson())
      OfficialVisitsSynchronisationMessageType.RETRY_SYNCHRONISATION_OFFICIAL_VISIT_MAPPING -> visitsService.retryCreateOfficialVisitMapping(message.fromJson())
      OfficialVisitsSynchronisationMessageType.RETRY_SYNCHRONISATION_OFFICIAL_VISITOR_MAPPING -> visitsService.retryCreateOfficialVisitorMapping(message.fromJson())
    }
  }
}

enum class OfficialVisitsSynchronisationMessageType {
  RETRY_SYNCHRONISATION_TIME_SLOT_MAPPING,
  RETRY_SYNCHRONISATION_VISIT_SLOT_MAPPING,
  RETRY_SYNCHRONISATION_OFFICIAL_VISIT_MAPPING,
  RETRY_SYNCHRONISATION_OFFICIAL_VISITOR_MAPPING,
}

data class VisitEvent(
  val visitId: Long,
  val bookingId: Long,
  val offenderIdDisplay: String,
  val agencyLocationId: String,
  override val auditModuleName: String,
) : EventAudited

data class VisitVisitorEvent(
  val visitVisitorId: Long,
  val visitId: Long,
  val bookingId: Long,
  val personId: Long?,
  val offenderIdDisplay: String,
  override val auditModuleName: String,
) : EventAudited

data class AgencyVisitTimeEvent(
  val agencyLocationId: String,
  val timeslotSequence: Int,
  val weekDay: String,
  override val auditModuleName: String,
) : EventAudited

data class AgencyVisitSlotEvent(
  val agencyVisitSlotId: Long,
  val agencyLocationId: String,
  val timeslotSequence: Int,
  val weekDay: String,
  override val auditModuleName: String,
) : EventAudited
