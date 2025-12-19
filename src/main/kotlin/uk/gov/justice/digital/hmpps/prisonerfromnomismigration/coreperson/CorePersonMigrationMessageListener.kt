package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CORE_PERSON_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class CorePersonMigrationMessageListener(
  objectMapper: ObjectMapper,
  migrationService: CorePersonMigrationService,
) : MigrationMessageListener(
  objectMapper,
  migrationService,
) {

  @SqsListener(
    CORE_PERSON_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  @WithSpan(value = "dps-syscon-migration_coreperson_queue", kind = SpanKind.SERVER)
  fun onCorePersonMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)
}
