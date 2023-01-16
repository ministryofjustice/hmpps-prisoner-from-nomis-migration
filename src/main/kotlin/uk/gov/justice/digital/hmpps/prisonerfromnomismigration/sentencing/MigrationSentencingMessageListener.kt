package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.amazon.sqs.javamessaging.message.SQSTextMessage
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.context
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.CANCEL_MIGRATE_SENTENCING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.MIGRATE_SENTENCE_ADJUSTMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.MIGRATE_SENTENCE_ADJUSTMENTS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.MIGRATE_SENTENCE_ADJUSTMENTS_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.MIGRATE_SENTENCING_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.RETRY_SENTENCE_ADJUSTMENT_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SENTENCING_QUEUE_ID

@Service
class MigrationSentencingMessageListener(
  private val objectMapper: ObjectMapper,
  private val sentencingMigrationService: SentencingMigrationService
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = SENTENCING_QUEUE_ID, containerFactory = "hmppsQueueContainerFactoryProxy", concurrency = "5")
  fun onMessage(message: String, rawMessage: SQSTextMessage) {
    log.debug("Received message {}", message)
    val migrationMessage: MigrationMessage<SentencingMessages, *> = message.fromJson()
    kotlin.runCatching {
      when (migrationMessage.type) {
        MIGRATE_SENTENCE_ADJUSTMENTS -> sentencingMigrationService.divideSentenceAdjustmentsByPage(context(message.fromJson()))
        MIGRATE_SENTENCE_ADJUSTMENTS_BY_PAGE -> sentencingMigrationService.migrateSentenceAdjustmentsForPage(context(message.fromJson()))
        MIGRATE_SENTENCE_ADJUSTMENT -> sentencingMigrationService.migrateSentenceAdjustment(context(message.fromJson()))
        MIGRATE_SENTENCING_STATUS_CHECK -> sentencingMigrationService.migrateSentencingStatusCheck(context(message.fromJson()))
        CANCEL_MIGRATE_SENTENCING -> sentencingMigrationService.cancelMigrateSentencingStatusCheck(context(message.fromJson()))
        RETRY_SENTENCE_ADJUSTMENT_MAPPING -> sentencingMigrationService.retryCreateSentenceAdjustmentMapping(context(message.fromJson()))
        // NG -> incentivesMigrationService.retryCreateIncentiveMapping(context(message.fromJson()))
        // SYNCHRONISE_CURRENT_INCENTIVE -> incentivesSynchronisationService.handleSynchroniseCurrentIncentiveMessage(context(message.fromJson()))
        // RETRY_INCENTIVE_SYNCHRONISATION_MAPPING -> incentivesSynchronisationService.retryCreateIncentiveMapping(context(message.fromJson()))
      }
    }.onFailure {
      log.error("MessageID:${rawMessage.sqsMessageId}", it)
      throw it
    }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this, object : TypeReference<T>() {})
}
