package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.prisonperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonPersonMigrationMappingRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.PrisonerPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PRISONPERSON_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class PrisonPersonMigrationMessageListener(
  objectMapper: ObjectMapper,
  prisonPersonMigrationService: PrisonPersonMigrationService,
) : MigrationMessageListener<PrisonPersonMigrationFilter, PrisonerId, PrisonerPhysicalAttributesResponse, PrisonPersonMigrationMappingRequest>(
  objectMapper,
  prisonPersonMigrationService,
) {

  @SqsListener(
    PRISONPERSON_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  @WithSpan(value = "dps-syscon-migration_prisonperson_queue", kind = SpanKind.SERVER)
  fun onPrisonPersonMessage(message: String, rawMessage: Message): CompletableFuture<Void>? = onMessage(message, rawMessage)

  override fun parseContextFilter(json: String): MigrationMessage<*, PrisonPersonMigrationFilter> = objectMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<PrisonPersonMigrationFilter>> = objectMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonerId> = objectMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, PrisonPersonMigrationMappingRequest> = objectMapper.readValue(json)
}
