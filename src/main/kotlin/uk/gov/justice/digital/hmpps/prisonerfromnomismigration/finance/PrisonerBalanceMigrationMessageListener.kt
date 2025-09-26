package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonerBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PRISONER_BALANCE_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class PrisonerBalanceMigrationMessageListener(
  objectMapper: ObjectMapper,
  migrationService: PrisonerBalanceMigrationService,
) : MigrationMessageListener<PrisonerBalanceMigrationFilter, Long, PrisonerBalanceDto, PrisonerBalanceMappingDto>(
  objectMapper,
  migrationService,
) {

  @SqsListener(
    PRISONER_BALANCE_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  @WithSpan(value = "dps-syscon-migration_prisonerbalance_queue", kind = SpanKind.SERVER)
  fun onPrisonerBalanceMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)

  override fun parseContextFilter(json: String): MigrationMessage<*, PrisonerBalanceMigrationFilter> = objectMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<PrisonerBalanceMigrationFilter>> = objectMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, Long> = objectMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, PrisonerBalanceMappingDto> = objectMapper.readValue(json)
}
