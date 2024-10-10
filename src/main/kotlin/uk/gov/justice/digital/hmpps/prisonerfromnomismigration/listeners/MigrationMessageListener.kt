package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.CANCEL_MIGRATION
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_ENTITIES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_ENTITY
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.MIGRATE_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.RETRY_MIGRATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.LocalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationService
import java.util.concurrent.CompletableFuture

abstract class MigrationMessageListener<FILTER : Any, NOMIS_ID : Any, NOMIS_ENTITY : Any, MAPPING : Any>(
  internal val objectMapper: ObjectMapper,
  private val migrationService: MigrationService<FILTER, NOMIS_ID, MAPPING>,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun onMessage(message: String, rawMessage: Message): CompletableFuture<Void>? {
    log.debug("Received message {}", message)
    val migrationMessage: LocalMessage<MigrationMessageType> = message.fromJson()
    return asCompletableFuture {
      runCatching {
        when (migrationMessage.type) {
          MIGRATE_ENTITIES -> migrationService.divideEntitiesByPage(migrationContextFilter(parseContextFilter(message)))
          MIGRATE_BY_PAGE -> migrationService.migrateEntitiesForPage(
            migrationContextFilter(parseContextPageFilter(message)),
          )
          MIGRATE_ENTITY -> migrationService.migrateNomisEntity(migrationContextFilter(parseContextNomisId(message)))
          MIGRATE_STATUS_CHECK -> migrationService.migrateStatusCheck(migrationContext(message.fromJson()))
          CANCEL_MIGRATION -> migrationService.cancelMigrateStatusCheck(migrationContext(message.fromJson()))
          RETRY_MIGRATION_MAPPING -> migrationService.retryCreateMapping(
            migrationContextFilter(
              parseContextMapping(message),
            ),
          )
        }
      }.onFailure {
        log.error("MessageID:${rawMessage.messageId()}", it)
        throw it
      }
    }.thenAccept { }
  }

  private fun <T> migrationContextFilter(message: MigrationMessage<*, T>): MigrationContext<T> =
    message.context

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this, object : TypeReference<T>() {})

  abstract fun parseContextFilter(json: String): MigrationMessage<*, FILTER>
  abstract fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<FILTER>>
  abstract fun parseContextNomisId(json: String): MigrationMessage<*, NOMIS_ID>
  abstract fun parseContextMapping(json: String): MigrationMessage<*, MAPPING>
}
