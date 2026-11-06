package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment

import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.BED_ASSIGNMENT_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class BedAssignmentMigrationMessageListener(
  jsonMapper: JsonMapper,
  migrationService: BedAssignmentMigrationService,
) : MigrationMessageListener(jsonMapper, migrationService) {
  @SqsListener(BED_ASSIGNMENT_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun onMigrationMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(
    message,
    rawMessage,
  )
}
