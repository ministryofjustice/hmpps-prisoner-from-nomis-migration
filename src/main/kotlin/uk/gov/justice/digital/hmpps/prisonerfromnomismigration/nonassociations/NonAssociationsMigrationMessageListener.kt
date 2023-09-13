package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.NonAssociationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NonAssociationIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.NonAssociationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NON_ASSOCIATIONS_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class NonAssociationsMigrationMessageListener(
  objectMapper: ObjectMapper,
  nonAssociationsMigrationService: NonAssociationsMigrationService,
) : MigrationMessageListener<NonAssociationsMigrationFilter, NonAssociationIdResponse, NonAssociationResponse, NonAssociationMappingDto>(
  objectMapper,
  nonAssociationsMigrationService,
) {

  @SqsListener(NON_ASSOCIATIONS_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "dps-syscon-migration_nonassociations_queue", kind = SpanKind.SERVER)
  fun onNonAssociationMessage(message: String, rawMessage: Message): CompletableFuture<Void>? {
    return onMessage(message, rawMessage)
  }

  override fun parseContextFilter(json: String): MigrationMessage<*, NonAssociationsMigrationFilter> {
    return objectMapper.readValue(json)
  }

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<NonAssociationsMigrationFilter>> {
    return objectMapper.readValue(json)
  }

  override fun parseContextNomisId(json: String): MigrationMessage<*, NonAssociationIdResponse> {
    return objectMapper.readValue(json)
  }

  override fun parseContextMapping(json: String): MigrationMessage<*, NonAssociationMappingDto> {
    return objectMapper.readValue(json)
  }
}
