package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import com.amazon.sqs.javamessaging.message.SQSTextMessage
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentiveMessages.CANCEL_MIGRATE_INCENTIVES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentiveMessages.MIGRATE_INCENTIVE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentiveMessages.MIGRATE_INCENTIVES
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentiveMessages.MIGRATE_INCENTIVES_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentiveMessages.MIGRATE_INCENTIVES_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentiveMessages.RETRY_INCENTIVE_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives.IncentiveMessages.RETRY_INCENTIVE_SYNCHRONISATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.context
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.INCENTIVES_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage

@Service
class MigrationIncentivesMessageListener(
  private val objectMapper: ObjectMapper,
  private val incentivesMigrationService: IncentivesMigrationService,
  private val incentivesSynchronisationService: IncentivesSynchronisationService
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = INCENTIVES_QUEUE_ID, containerFactory = "hmppsQueueContainerFactoryProxy", concurrency = "5")
  fun onMessage(message: String, rawMessage: SQSTextMessage) {
    log.debug("Received message {}", message)
    val migrationMessage: MigrationMessage<IncentiveMessages, *> = message.fromJson()
    kotlin.runCatching {
      when (migrationMessage.type) {
        MIGRATE_INCENTIVES -> incentivesMigrationService.divideIncentivesByPage(context(message.fromJson()))
        MIGRATE_INCENTIVES_BY_PAGE -> incentivesMigrationService.migrateIncentivesForPage(context(message.fromJson()))
        MIGRATE_INCENTIVE -> incentivesMigrationService.migrateIncentive(context(message.fromJson()))
        MIGRATE_INCENTIVES_STATUS_CHECK -> incentivesMigrationService.migrateIncentivesStatusCheck(context(message.fromJson()))
        CANCEL_MIGRATE_INCENTIVES -> incentivesMigrationService.cancelMigrateIncentivesStatusCheck(context(message.fromJson()))
        RETRY_INCENTIVE_MAPPING -> incentivesMigrationService.retryCreateIncentiveMapping(context(message.fromJson()))
        RETRY_INCENTIVE_SYNCHRONISATION_MAPPING -> incentivesSynchronisationService.retryCreateIncentiveMapping(context(message.fromJson()))
      }
    }.onFailure {
      log.error("MessageID:${rawMessage.sqsMessageId}", it)
      throw it
    }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this, object : TypeReference<T>() {})
}
