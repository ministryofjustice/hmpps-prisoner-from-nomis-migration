package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentiveMessages.CANCEL_MIGRATE_INCENTIVES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentiveMessages.MIGRATE_INCENTIVE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentiveMessages.MIGRATE_INCENTIVES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentiveMessages.MIGRATE_INCENTIVES_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentiveMessages.MIGRATE_INCENTIVES_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentiveMessages.RETRY_INCENTIVE_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.migrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.INCENTIVES_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import java.util.concurrent.CompletableFuture

@Service
class MigrationIncentivesMessageListener(
  private val objectMapper: ObjectMapper,
  private val incentivesMigrationService: IncentivesMigrationService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(INCENTIVES_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "dps-syscon-migration_incentives_queue", kind = SpanKind.SERVER)
  fun onMessage(message: String, rawMessage: Message): CompletableFuture<Void> {
    log.debug("Received message {}", message)
    val migrationMessage: MigrationMessage<IncentiveMessages, *> = message.fromJson()
    return CoroutineScope(Dispatchers.Default).future {
      runCatching {
        when (migrationMessage.type) {
          MIGRATE_INCENTIVES -> incentivesMigrationService.divideIncentivesByPage(migrationContext(message.fromJson()))
          MIGRATE_INCENTIVES_BY_PAGE -> incentivesMigrationService.migrateIncentivesForPage(migrationContext(message.fromJson()))
          MIGRATE_INCENTIVE -> incentivesMigrationService.migrateIncentive(migrationContext(message.fromJson()))
          MIGRATE_INCENTIVES_STATUS_CHECK -> incentivesMigrationService.migrateIncentivesStatusCheck(migrationContext(message.fromJson()))
          CANCEL_MIGRATE_INCENTIVES -> incentivesMigrationService.cancelMigrateIncentivesStatusCheck(migrationContext(message.fromJson()))
          RETRY_INCENTIVE_MAPPING -> incentivesMigrationService.retryCreateIncentiveMapping(migrationContext(message.fromJson()))
          else -> { log.error("Received unexpected message: ${migrationMessage.type}") }
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
