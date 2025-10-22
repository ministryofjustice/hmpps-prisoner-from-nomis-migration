package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits

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
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.OFFICIAL_VISITS_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class OfficialVisitsMigrationMessageListener(
  objectMapper: ObjectMapper,
  migrationService: OfficialVisitsMigrationService,
) : MigrationMessageListener<Any, Any, Any, Any>(
  objectMapper,
  migrationService,
) {

  @SqsListener(
    OFFICIAL_VISITS_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  @WithSpan(value = "dps-syscon-migration_officialvisits_queue", kind = SpanKind.SERVER)
  fun onMigrationMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)

  override fun parseContextFilter(json: String): MigrationMessage<*, Any> = objectMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<Any>> = objectMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, Any> = objectMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, Any> = objectMapper.readValue(json)
}
