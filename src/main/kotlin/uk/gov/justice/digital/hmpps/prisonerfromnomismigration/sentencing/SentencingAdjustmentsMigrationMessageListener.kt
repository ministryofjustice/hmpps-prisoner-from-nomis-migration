package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AdjustmentIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SENTENCING_ADJUSTMENTS_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class SentencingAdjustmentsMigrationMessageListener(
  objectMapper: ObjectMapper,
  sentencingAdjustmentsMigrationService: SentencingAdjustmentsMigrationService,
) : MigrationMessageListener<SentencingMigrationFilter, AdjustmentIdResponse, NomisAdjustment, SentencingAdjustmentNomisMapping>(
  objectMapper,
  sentencingAdjustmentsMigrationService,
) {

  @SqsListener(SENTENCING_ADJUSTMENTS_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  fun onSentencingMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)

  override fun parseContextFilter(json: String): MigrationMessage<*, SentencingMigrationFilter> = objectMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<SentencingMigrationFilter>> = objectMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, AdjustmentIdResponse> = objectMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, SentencingAdjustmentNomisMapping> = objectMapper.readValue(json)
}
