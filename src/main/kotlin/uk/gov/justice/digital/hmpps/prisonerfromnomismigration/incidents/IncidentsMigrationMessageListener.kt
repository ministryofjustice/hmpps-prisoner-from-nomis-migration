package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.IncidentMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IncidentIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.IncidentResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.INCIDENTS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import java.util.concurrent.CompletableFuture

@Service
class IncidentsMigrationMessageListener(
  objectMapper: ObjectMapper,
  incidentsMigrationService: IncidentsMigrationService,
) : MigrationMessageListener<IncidentsMigrationFilter, IncidentIdResponse, IncidentResponse, IncidentMappingDto>(
  objectMapper,
  incidentsMigrationService,
) {

  @SqsListener(INCIDENTS_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy", maxConcurrentMessages = "8", maxMessagesPerPoll = "8")
  @WithSpan(value = "dps-syscon-migration_incidents_queue", kind = SpanKind.SERVER)
  fun onIncidentMessage(message: String, rawMessage: Message): CompletableFuture<Void?> {
    return onMessage(message, rawMessage)
  }

  override fun parseContextFilter(json: String): MigrationMessage<*, IncidentsMigrationFilter> {
    return objectMapper.readValue(json)
  }

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<IncidentsMigrationFilter>> {
    return objectMapper.readValue(json)
  }

  override fun parseContextNomisId(json: String): MigrationMessage<*, IncidentIdResponse> {
    return objectMapper.readValue(json)
  }

  override fun parseContextMapping(json: String): MigrationMessage<*, IncidentMappingDto> {
    return objectMapper.readValue(json)
  }
}
