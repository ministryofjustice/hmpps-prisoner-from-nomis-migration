package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court

import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.COURT_MOVEMENTS_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class CourtSchedulerMigrationMessageListener(
  jsonMapper: JsonMapper,
  migrationService: CourtSchedulerMigrationService,
) : MigrationMessageListener(
  jsonMapper,
  migrationService,
) {

  @SqsListener(
    COURT_MOVEMENTS_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  fun onExternalMovementMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)
}
