package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CorePersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CorePerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CORE_PERSON_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import java.util.concurrent.CompletableFuture

@Service
class CorePersonMigrationMessageListener(
  objectMapper: ObjectMapper,
  migrationService: CorePersonMigrationService,
) : MigrationMessageListener<CorePersonMigrationFilter, PrisonerId, CorePerson, CorePersonMappingsDto>(
  objectMapper,
  migrationService,
) {

  @SqsListener(
    CORE_PERSON_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  @WithSpan(value = "dps-syscon-migration_coreperson_queue", kind = SpanKind.SERVER)
  fun onCorePersonMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)

  override fun parseContextFilter(json: String): MigrationMessage<*, CorePersonMigrationFilter> = objectMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<CorePersonMigrationFilter>> = objectMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonerId> = objectMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, CorePersonMappingsDto> = objectMapper.readValue(json)
}
