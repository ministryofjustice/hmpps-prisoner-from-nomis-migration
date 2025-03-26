package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visitbalances

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.VisitBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceDetailResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.VisitBalanceIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VISIT_BALANCE_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class VisitBalanceMigrationMessageListener(
  objectMapper: ObjectMapper,
  migrationService: VisitBalanceMigrationService,
) : MigrationMessageListener<VisitBalanceMigrationFilter, VisitBalanceIdResponse, VisitBalanceDetailResponse, VisitBalanceMappingDto>(
  objectMapper,
  migrationService,
) {

  @SqsListener(
    VISIT_BALANCE_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  @WithSpan(value = "dps-syscon-migration_visitbalance_queue", kind = SpanKind.SERVER)
  fun onVisitBalanceMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)

  override fun parseContextFilter(json: String): MigrationMessage<*, VisitBalanceMigrationFilter> = objectMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<VisitBalanceMigrationFilter>> = objectMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, VisitBalanceIdResponse> = objectMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, VisitBalanceMappingDto> = objectMapper.readValue(json)
}
