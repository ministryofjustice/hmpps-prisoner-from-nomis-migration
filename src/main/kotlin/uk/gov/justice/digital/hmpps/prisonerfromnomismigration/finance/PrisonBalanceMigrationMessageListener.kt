package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.PrisonBalanceMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonBalanceDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.PRISON_BALANCE_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class PrisonBalanceMigrationMessageListener(
  objectMapper: ObjectMapper,
  migrationService: PrisonBalanceMigrationService,
) : MigrationMessageListener<PrisonBalanceMigrationFilter, String, PrisonBalanceDto, PrisonBalanceMappingDto>(
  objectMapper,
  migrationService,
) {

  @SqsListener(
    PRISON_BALANCE_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  @WithSpan(value = "dps-syscon-migration_prisonbalance_queue", kind = SpanKind.SERVER)
  fun onPrisonBalanceMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)

  override fun parseContextFilter(json: String): MigrationMessage<*, PrisonBalanceMigrationFilter> = objectMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<PrisonBalanceMigrationFilter, String>> = objectMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, String> = objectMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, PrisonBalanceMappingDto> = objectMapper.readValue(json)
}
