package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property

import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PROPERTY_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class PropertyMigrationMessageListener(
  jsonMapper: JsonMapper,
  migrationService: PropertyMigrationService,
) : MigrationMessageListener(jsonMapper, migrationService) {

  @SqsListener(PROPERTY_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun onMigrationMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(
    message,
    rawMessage,
  )
}
