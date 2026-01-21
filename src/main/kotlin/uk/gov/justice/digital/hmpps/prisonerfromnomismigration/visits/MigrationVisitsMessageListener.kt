package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VISITS_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class MigrationVisitsMessageListener(
  jsonMapper: JsonMapper,
  visitsMigrationService: VisitsMigrationService,
) : MigrationMessageListener(
  jsonMapper,
  visitsMigrationService,
) {

  @SqsListener(VISITS_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "dps-syscon-migration_visits_queue", kind = SpanKind.SERVER)
  fun onVisitMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)
}
