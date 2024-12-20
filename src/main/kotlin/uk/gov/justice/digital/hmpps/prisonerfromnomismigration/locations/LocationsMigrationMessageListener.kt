package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.LocationIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.LocationResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.LOCATIONS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import java.util.concurrent.CompletableFuture

@Service
class LocationsMigrationMessageListener(
  objectMapper: ObjectMapper,
  locationsMigrationService: LocationsMigrationService,
) : MigrationMessageListener<LocationsMigrationFilter, LocationIdResponse, LocationResponse, LocationMappingDto>(
  objectMapper,
  locationsMigrationService,
) {

  @SqsListener(LOCATIONS_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "dps-syscon-migration_locations_queue", kind = SpanKind.SERVER)
  fun onLocationMessage(message: String, rawMessage: Message): CompletableFuture<Void?> {
    return onMessage(message, rawMessage)
  }

  override fun parseContextFilter(json: String): MigrationMessage<*, LocationsMigrationFilter> {
    return objectMapper.readValue(json)
  }

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<LocationsMigrationFilter>> {
    return objectMapper.readValue(json)
  }

  override fun parseContextNomisId(json: String): MigrationMessage<*, LocationIdResponse> {
    return objectMapper.readValue(json)
  }

  override fun parseContextMapping(json: String): MigrationMessage<*, LocationMappingDto> {
    return objectMapper.readValue(json)
  }
}
