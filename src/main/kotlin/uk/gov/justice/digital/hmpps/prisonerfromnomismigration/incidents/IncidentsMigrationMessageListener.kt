package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents

import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.INCIDENTS_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class IncidentsMigrationMessageListener(
  jsonMapper: JsonMapper,
  incidentsMigrationService: IncidentsMigrationService,
) : MigrationMessageListener(
  jsonMapper,
  incidentsMigrationService,
) {

  @SqsListener(INCIDENTS_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy", maxConcurrentMessages = "8", maxMessagesPerPoll = "8")
  @WithSpan(value = "dps-syscon-migration_incidents_queue", kind = SpanKind.SERVER)
  fun onIncidentMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)
}
