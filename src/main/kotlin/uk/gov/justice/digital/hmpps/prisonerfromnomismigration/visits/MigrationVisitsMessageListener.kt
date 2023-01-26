package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.context
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VISITS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitMessages.CANCEL_MIGRATE_VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitMessages.MIGRATE_VISIT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitMessages.MIGRATE_VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitMessages.MIGRATE_VISITS_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitMessages.MIGRATE_VISITS_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitMessages.RETRY_VISIT_MAPPING
import java.util.concurrent.CompletableFuture

@Service
class MigrationVisitsMessageListener(
  private val objectMapper: ObjectMapper,
  private val visitsMigrationService: VisitsMigrationService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(VISITS_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String, rawMessage: Message): CompletableFuture<Void>? {
    log.debug("Received message {}", message)
    val migrationMessage: MigrationMessage<VisitMessages, *> = message.fromJson()
    return CoroutineScope(Dispatchers.Default).future {
      runCatching {
        when (migrationMessage.type) {
          MIGRATE_VISITS -> visitsMigrationService.divideVisitsByPage(context(message.fromJson()))
          MIGRATE_VISITS_BY_PAGE -> visitsMigrationService.migrateVisitsForPage(context(message.fromJson()))
          MIGRATE_VISIT -> visitsMigrationService.migrateVisit(context(message.fromJson()))
          RETRY_VISIT_MAPPING -> visitsMigrationService.retryCreateVisitMapping(context(message.fromJson()))
          MIGRATE_VISITS_STATUS_CHECK -> visitsMigrationService.migrateVisitsStatusCheck(context(message.fromJson()))
          CANCEL_MIGRATE_VISITS -> visitsMigrationService.cancelMigrateVisitsStatusCheck(context(message.fromJson()))
        }
      }.onFailure {
        log.error("MessageID:${rawMessage.messageId()}", it)
        throw it
      }
    }.thenAccept { }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this, object : TypeReference<T>() {})
}
