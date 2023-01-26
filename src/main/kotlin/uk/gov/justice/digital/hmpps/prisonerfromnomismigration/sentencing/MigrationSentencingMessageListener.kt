package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.awspring.cloud.sqs.annotation.SqsListener
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.context
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.CANCEL_MIGRATE_SENTENCING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.MIGRATE_SENTENCE_ADJUSTMENTS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.MIGRATE_SENTENCING_ADJUSTMENT
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.MIGRATE_SENTENCING_ADJUSTMENTS_BY_PAGE
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.MIGRATE_SENTENCING_STATUS_CHECK
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingMessages.RETRY_SENTENCING_ADJUSTMENT_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SENTENCING_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class MigrationSentencingMessageListener(
  private val objectMapper: ObjectMapper,
  private val sentencingMigrationService: SentencingMigrationService
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @OptIn(DelicateCoroutinesApi::class)
  @SqsListener(SENTENCING_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String, rawMessage: Message): CompletableFuture<Void>? {
    log.debug("Received message {}", message)
    val migrationMessage: MigrationMessage<SentencingMessages, *> = message.fromJson()
    return GlobalScope.launch {
      runCatching {
        when (migrationMessage.type) {
          MIGRATE_SENTENCE_ADJUSTMENTS -> sentencingMigrationService.divideSentencingAdjustmentsByPage(context(message.fromJson()))
          MIGRATE_SENTENCING_ADJUSTMENTS_BY_PAGE -> sentencingMigrationService.migrateSentenceAdjustmentsForPage(
            context(
              message.fromJson()
            )
          )

          MIGRATE_SENTENCING_ADJUSTMENT -> sentencingMigrationService.migrateSentencingAdjustment(context(message.fromJson()))
          MIGRATE_SENTENCING_STATUS_CHECK -> sentencingMigrationService.migrateSentencingStatusCheck(context(message.fromJson()))
          CANCEL_MIGRATE_SENTENCING -> sentencingMigrationService.cancelMigrateSentencingStatusCheck(context(message.fromJson()))
          RETRY_SENTENCING_ADJUSTMENT_MAPPING -> sentencingMigrationService.retryCreateSentenceAdjustmentMapping(
            context(
              message.fromJson()
            )
          )
          // NG -> incentivesMigrationService.retryCreateIncentiveMapping(context(message.fromJson()))
          // SYNCHRONISE_CURRENT_INCENTIVE -> incentivesSynchronisationService.handleSynchroniseCurrentIncentiveMessage(context(message.fromJson()))
          // RETRY_INCENTIVE_SYNCHRONISATION_MAPPING -> incentivesSynchronisationService.retryCreateIncentiveMapping(context(message.fromJson()))
        }
      }.onFailure {
        log.error("MessageID:${rawMessage.messageId()}", it)
        throw it
      }
    }.asCompletableFuture().thenAccept { }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this, object : TypeReference<T>() {})
}
