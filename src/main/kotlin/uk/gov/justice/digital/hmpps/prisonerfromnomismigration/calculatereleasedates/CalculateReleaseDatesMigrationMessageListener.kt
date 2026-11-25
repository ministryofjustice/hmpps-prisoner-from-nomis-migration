package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.calculatereleasedates

import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CALCULATE_RELEASE_DATES_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class CalculateReleaseDatesMigrationMessageListener(
  jsonMapper: JsonMapper,
  migrationService: CalculateReleaseDatesMigrationService,
) : MigrationMessageListener(jsonMapper, migrationService) {
  @SqsListener(CALCULATE_RELEASE_DATES_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun onMigrationMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)
}
