package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.CANCEL_MIGRATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_BY_DIVISION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_ENTITIES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_ENTITY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.RETRY_MIGRATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.LocalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import java.util.concurrent.CompletableFuture

abstract class MigrationMessageListener(
  internal val jsonMapper: JsonMapper,
  private val migrationService: MigrationService<*, *, *, *>,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun onMessage(message: String, rawMessage: Message): CompletableFuture<Void?> {
    log.debug("Received message {}", message)
    val migrationMessage: LocalMessage<MigrationMessageType> = message.fromJson()
    return asCompletableFuture {
      runCatching {
        when (migrationMessage.type) {
          MIGRATE_ENTITIES -> migrationService.divideEntitiesByPage(message)
          MIGRATE_BY_DIVISION -> migrationService.divideEntitiesByDivision(message)
          MIGRATE_BY_PAGE -> migrationService.migrateEntitiesForPage(message)
          MIGRATE_ENTITY -> migrationService.migrateNomisEntity(message)
          MIGRATE_STATUS_CHECK -> migrationService.migrateStatusCheck(message)
          CANCEL_MIGRATION -> migrationService.cancelMigrateStatusCheck(message)
          RETRY_MIGRATION_MAPPING -> migrationService.retryCreateMapping(message)
        }
      }.onFailure {
        log.error("MessageID:${rawMessage.messageId()}", it)
        throw it
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}
