package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.religion

import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CORE_PERSON_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class ReligionsMigrationMessageListener(
  jsonMapper: JsonMapper,
  migrationService: ReligionsMigrationService,
) : MigrationMessageListener(
  jsonMapper,
  migrationService,
) {

  @SqsListener(
    CORE_PERSON_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  fun onMigrationMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)
}
