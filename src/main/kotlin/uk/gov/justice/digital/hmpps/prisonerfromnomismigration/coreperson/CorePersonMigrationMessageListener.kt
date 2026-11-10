package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CORE_PERSON_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.CORE_PERSON_ADDRESS_CONTACT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType.CORE_PERSON_ALIAS_IDENTIFIER
import java.util.concurrent.CompletableFuture

/**
 * The core person migration queue is shared by several migration types (which are never run at the same time),
 * so this proxies each message to the listener for the migration type held in the message context.
 */
@Service
class CorePersonMigrationMessageListener(
  private val jsonMapper: JsonMapper,
  addressContactMigrationService: CorePersonAddressContactMigrationService,
  aliasIdentifierMigrationService: CorePersonAliasIdentifierMigrationService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private val listeners: Map<MigrationType, MigrationMessageListener> = mapOf(
    CORE_PERSON_ADDRESS_CONTACT to listenerFor(addressContactMigrationService),
    CORE_PERSON_ALIAS_IDENTIFIER to listenerFor(aliasIdentifierMigrationService),
  )

  private fun listenerFor(migrationService: MigrationService<*, *, *, *>) = object : MigrationMessageListener(jsonMapper, migrationService) {}

  @SqsListener(
    CORE_PERSON_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  fun onMigrationMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = runCatching {
    val migrationType = jsonMapper.readValue<CorePersonMigrationMessage>(message).context.type
    val listener = listeners[migrationType] ?: throw IllegalStateException("No core person migration listener for migration type $migrationType")
    listener.onMessage(message, rawMessage)
  }.getOrElse {
    log.error("MessageID:${rawMessage.messageId()}", it)
    CompletableFuture.failedFuture(it)
  }
}

internal data class CorePersonMigrationMessage(val context: CorePersonMigrationContext)
internal data class CorePersonMigrationContext(val type: MigrationType)
