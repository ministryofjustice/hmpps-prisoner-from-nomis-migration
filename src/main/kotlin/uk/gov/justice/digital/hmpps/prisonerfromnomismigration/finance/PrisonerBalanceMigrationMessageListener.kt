package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PRISONER_BALANCE_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class PrisonerBalanceMigrationMessageListener(
  jsonMapper: JsonMapper,
  migrationService: PrisonerBalanceMigrationService,
) : MigrationMessageListener(
  jsonMapper,
  migrationService,
) {

  @SqsListener(
    PRISONER_BALANCE_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  fun onPrisonerBalanceMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)
}
