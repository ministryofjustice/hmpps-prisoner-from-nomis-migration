package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners

import com.amazon.sqs.javamessaging.message.SQSTextMessage
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISIT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.RETRY_VISIT_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitsMigrationService

@Service
class MigrationMessageListener(
  private val objectMapper: ObjectMapper,
  private val visitsMigrationService: VisitsMigrationService
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "migration", containerFactory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String, rawMessage: SQSTextMessage) {
    log.debug("Received message {}", message)
    val migrationMessage: MigrationMessage<*> = message.fromJson()
    kotlin.runCatching {
      when (migrationMessage.type) {
        MIGRATE_VISITS -> visitsMigrationService.divideVisitsByPage(context(message.fromJson()))
        MIGRATE_VISITS_BY_PAGE -> visitsMigrationService.migrateVisitsForPage(context(message.fromJson()))
        MIGRATE_VISIT -> visitsMigrationService.migrateVisit(context(message.fromJson()))
        RETRY_VISIT_MAPPING -> visitsMigrationService.retryCreateVisitMapping(context(message.fromJson()))
        MIGRATE_VISITS_STATUS_CHECK -> visitsMigrationService.migrateVisitsStatusCheck(context(message.fromJson()))
      }
    }.onFailure {
      log.error("MessageID:${rawMessage.sqsMessageId}", it)
      throw it
    }
  }

  class MigrationMessage<T>(
    val type: Messages,
    val context: MigrationContext<T>
  )

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this, object : TypeReference<T>() {})
}

private inline fun <reified T> context(message: MigrationMessage<T>): MigrationContext<T> =
  message.context
