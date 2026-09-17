package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.drugtesting

import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.DRUG_TESTING_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class DrugTestingMigrationMessageListener(
  jsonMapper: JsonMapper,
  migrationService: DrugTestingMigrationService,
) : MigrationMessageListener(
  jsonMapper,
  migrationService,
) {

  @SqsListener(
    DRUG_TESTING_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  fun onMigrationMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)
}
