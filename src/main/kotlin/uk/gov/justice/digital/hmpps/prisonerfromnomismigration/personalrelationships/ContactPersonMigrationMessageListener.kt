package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.ContactPerson
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PersonIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PERSONALRELATIONSHIPS_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class ContactPersonMigrationMessageListener(
  objectMapper: ObjectMapper,
  migrationService: ContactPersonMigrationService,
) : MigrationMessageListener<ContactPersonMigrationFilter, PersonIdResponse, ContactPerson, ContactPersonMappingsDto>(
  objectMapper,
  migrationService,
) {

  @SqsListener(
    PERSONALRELATIONSHIPS_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  @WithSpan(value = "dps-syscon-migration_personalrelationships_queue", kind = SpanKind.SERVER)
  fun onContactPersonMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)

  override fun parseContextFilter(json: String): MigrationMessage<*, ContactPersonMigrationFilter> = objectMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<ContactPersonMigrationFilter>> = objectMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, PersonIdResponse> = objectMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, ContactPersonMappingsDto> = objectMapper.readValue(json)
}
