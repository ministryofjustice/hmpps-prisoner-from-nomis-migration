package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisAdjustment
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisAdjustmentId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SENTENCING_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class MigrationSentencingMessageListener(
  objectMapper: ObjectMapper,
  sentencingMigrationService: SentencingMigrationService,
  sentencingSynchronisationService: SentencingSynchronisationService
) : MigrationMessageListener<SentencingMigrationFilter, NomisAdjustmentId, NomisAdjustment, SentencingAdjustmentNomisMapping>(objectMapper, sentencingMigrationService, sentencingSynchronisationService) {

  @SqsListener(SENTENCING_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "dps-syscon-migration_sentencing_queue", kind = SpanKind.SERVER)
  fun onSentencingMessage(message: String, rawMessage: Message): CompletableFuture<Void>? {
    return onMessage(message, rawMessage)
  }

  override fun parseContextFilter(json: String): MigrationMessage<*, SentencingMigrationFilter> {
    return objectMapper.readValue(json)
  }

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<SentencingMigrationFilter>> {
    return objectMapper.readValue(json)
  }

  override fun parseContextNomisId(json: String): MigrationMessage<*, NomisAdjustmentId> {
    return objectMapper.readValue(json)
  }

  override fun parseContextMapping(json: String): MigrationMessage<*, SentencingAdjustmentNomisMapping> {
    return objectMapper.readValue(json)
  }
}
