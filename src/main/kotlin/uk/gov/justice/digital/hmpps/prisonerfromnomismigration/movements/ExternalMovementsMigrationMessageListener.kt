package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements

import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.EXTERNAL_MOVEMENTS_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class ExternalMovementsMigrationMessageListener(
  jsonMapper: JsonMapper,
  migrationService: ExternalMovementsMigrationService,
) : MigrationMessageListener(
  jsonMapper,
  migrationService,
) {

  @SqsListener(
    EXTERNAL_MOVEMENTS_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  @WithSpan(value = "dps-syscon-migration_externalmovements_queue", kind = SpanKind.SERVER)
  fun onExternalMovementMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)
}
