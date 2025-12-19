package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PRISONER_BALANCE_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class PrisonerBalanceMigrationMessageListener(
  objectMapper: ObjectMapper,
  migrationService: PrisonerBalanceMigrationService,
) : MigrationMessageListener(
  objectMapper,
  migrationService,
) {

  @SqsListener(
    PRISONER_BALANCE_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  @WithSpan(value = "dps-syscon-migration_prisonerbalance_queue", kind = SpanKind.SERVER)
  fun onPrisonerBalanceMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)
}
