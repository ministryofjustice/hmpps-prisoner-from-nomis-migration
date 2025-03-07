package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.AllocationMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.FindActiveAllocationIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.GetAllocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ALLOCATIONS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import java.util.concurrent.CompletableFuture

@Service
class AllocationsMigrationMessageListener(
  objectMapper: ObjectMapper,
  migrationService: AllocationsMigrationService,
) : MigrationMessageListener<AllocationsMigrationFilter, FindActiveAllocationIdsResponse, GetAllocationResponse, AllocationMigrationMappingDto>(
  objectMapper,
  migrationService,
) {

  @SqsListener(ALLOCATIONS_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "dps-syscon-migration_allocations_queue", kind = SpanKind.SERVER)
  fun onActivitiesMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)

  override fun parseContextFilter(json: String): MigrationMessage<*, AllocationsMigrationFilter> = objectMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<AllocationsMigrationFilter>> = objectMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, FindActiveAllocationIdsResponse> = objectMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, AllocationMigrationMappingDto> = objectMapper.readValue(json)
}
