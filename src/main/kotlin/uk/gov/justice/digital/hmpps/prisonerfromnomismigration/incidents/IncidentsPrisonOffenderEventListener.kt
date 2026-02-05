package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.EventAudited
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType.RETRY_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.INCIDENTS_SYNC_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class IncidentsPrisonOffenderEventListener(
  private val incidentsSynchronisationService: IncidentsSynchronisationService,
  private val jsonMapper: JsonMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(INCIDENTS_SYNC_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String): CompletableFuture<Void?> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = jsonMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType, "incidents")) {
            when (eventType) {
              "INCIDENT-INSERTED" -> incidentsSynchronisationService.synchroniseIncidentInsert(sqsMessage.Message.fromJson())

              "INCIDENT-DELETED-CASES" -> incidentsSynchronisationService.synchroniseIncidentDelete(sqsMessage.Message.fromJson())

              "INCIDENT-CHANGED-CASES",
              "INCIDENT-CHANGED-PARTIES",
              "INCIDENT-CHANGED-RESPONSES",
              "INCIDENT-CHANGED-REQUIREMENTS",
              ->
                incidentsSynchronisationService.synchroniseIncidentUpdate(sqsMessage.Message.fromJson())

              // Deleting from a child table is just an update on the top level incident
              "INCIDENT-DELETED-PARTIES",
              "INCIDENT-DELETED-RESPONSES",
              "INCIDENT-DELETED-REQUIREMENTS",
              -> incidentsSynchronisationService.synchroniseIncidentUpdateDelete(sqsMessage.Message.fromJson())

              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }

        RETRY_SYNCHRONISATION_MAPPING.name -> incidentsSynchronisationService.retryCreateIncidentMapping(
          sqsMessage.Message.fromJson(),
        )
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

// Depending on the type of update/delete there will be additional params
// (e.g. incidentPartySeq for INCIDENT-CHANGED-PARTIES, but we're currently only interested in the whole incident, not just the changed item)
data class IncidentsOffenderEvent(
  val incidentCaseId: Long,
  override val auditModuleName: String?,
) : EventAudited
