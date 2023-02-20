package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.migrationContext
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
  @WithSpan(value = "dps-syscon-migration_visits_queue", kind = SpanKind.SERVER)
  fun onMessage(message: String, rawMessage: Message): CompletableFuture<Void>? {
    log.debug("Received message {}", message)
    val migrationMessage: MigrationMessage<VisitMessages, *> = message.fromJson()
    return CoroutineScope(Dispatchers.Default).future {
      runCatching {
        when (migrationMessage.type) {
          MIGRATE_VISITS -> visitsMigrationService.divideVisitsByPage(migrationContext(message.fromJson()))
          MIGRATE_VISITS_BY_PAGE -> visitsMigrationService.migrateVisitsForPage(migrationContext(message.fromJson()))
          MIGRATE_VISIT -> visitsMigrationService.migrateVisit(migrationContext(message.fromJson()))
          RETRY_VISIT_MAPPING -> visitsMigrationService.retryCreateVisitMapping(migrationContext(message.fromJson()))
          MIGRATE_VISITS_STATUS_CHECK -> visitsMigrationService.migrateVisitsStatusCheck(migrationContext(message.fromJson()))
          CANCEL_MIGRATE_VISITS -> visitsMigrationService.cancelMigrateVisitsStatusCheck(migrationContext(message.fromJson()))
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
