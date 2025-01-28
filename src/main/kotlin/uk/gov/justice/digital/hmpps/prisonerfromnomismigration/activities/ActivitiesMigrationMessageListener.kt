package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.ActivityMigrationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.FindActiveActivityIdsResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.GetActivityResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.ACTIVITIES_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import java.util.concurrent.CompletableFuture

@Service
class ActivitiesMigrationMessageListener(
  objectMapper: ObjectMapper,
  migrationService: ActivitiesMigrationService,
) : MigrationMessageListener<ActivitiesMigrationFilter, FindActiveActivityIdsResponse, GetActivityResponse, ActivityMigrationMappingDto>(
  objectMapper,
  migrationService,
) {

  @SqsListener(ACTIVITIES_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "dps-syscon-migration_activities_queue", kind = SpanKind.SERVER)
  fun onActivitiesMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)

  override fun parseContextFilter(json: String): MigrationMessage<*, ActivitiesMigrationFilter> = objectMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<ActivitiesMigrationFilter>> = objectMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, FindActiveActivityIdsResponse> = objectMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, ActivityMigrationMappingDto> = objectMapper.readValue(json)
}
