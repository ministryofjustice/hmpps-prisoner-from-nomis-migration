package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra

import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CSRA_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class CsraMigrationMessageListener(
  jsonMapper: JsonMapper,
  migrationService: CsraMigrationService,
) : MigrationMessageListener(jsonMapper, migrationService) {

  @SqsListener(CSRA_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "dps-syscon-migration_csra_queue", kind = SpanKind.SERVER)
  fun onMigrationMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(
    message,
    rawMessage,
  )
}
