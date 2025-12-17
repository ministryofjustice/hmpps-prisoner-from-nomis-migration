package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseBatchMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.COURT_SENTENCING_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import java.util.concurrent.CompletableFuture

@Service
class CourtSentencingMigrationMessageListener(
  objectMapper: ObjectMapper,
  courtSentencingMigrationService: CourtSentencingMigrationService,
) : MigrationMessageListener<CourtSentencingMigrationFilter, PrisonerId, List<CourtCaseResponse>, CourtCaseMigrationMapping>(
  objectMapper,
  courtSentencingMigrationService,
) {

  @SqsListener(
    COURT_SENTENCING_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  @WithSpan(value = "dps-syscon-migration_courtsentencing_queue", kind = SpanKind.SERVER)
  fun onCourtSentencingMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)

  override fun parseContextFilter(json: String): MigrationMessage<*, CourtSentencingMigrationFilter> = objectMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<CourtSentencingMigrationFilter, PrisonerId>> = objectMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, PrisonerId> = objectMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, CourtCaseMigrationMapping> = objectMapper.readValue(json)
}

data class CourtCaseMigrationMapping(
  val offenderNo: String,
  val mapping: CourtCaseBatchMappingDto,
)
