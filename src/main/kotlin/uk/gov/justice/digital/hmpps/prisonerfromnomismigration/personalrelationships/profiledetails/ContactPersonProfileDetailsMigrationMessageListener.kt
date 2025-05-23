package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.profiledetails

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ContactPersonProfileDetailsMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PERSONALRELATIONSHIPS_PROFILEDETAILS_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class ContactPersonProfileDetailsMigrationMessageListener(
  objectMapper: ObjectMapper,
  migrationService: ContactPersonProfileDetailsMigrationService,
) : MigrationMessageListener<ContactPersonProfileDetailsMigrationFilter, PrisonerId, PrisonerProfileDetailsResponse, ContactPersonProfileDetailsMigrationMappingDto>(
  objectMapper,
  migrationService,
) {

  @SqsListener(
    PERSONALRELATIONSHIPS_PROFILEDETAILS_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  @WithSpan(value = "dps-syscon-migration_personalrelationships_profiledetails_queue", kind = SpanKind.SERVER)
  fun onContactPersonMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)

  override fun parseContextFilter(json: String): MigrationMessage<*, ContactPersonProfileDetailsMigrationFilter> = objectMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<ContactPersonProfileDetailsMigrationFilter>> = objectMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonerId> = objectMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, ContactPersonProfileDetailsMigrationMappingDto> = objectMapper.readValue(json)
}
