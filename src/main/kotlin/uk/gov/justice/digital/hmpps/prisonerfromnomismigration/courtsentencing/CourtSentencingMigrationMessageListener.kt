package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseBatchMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.COURT_SENTENCING_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class CourtSentencingMigrationMessageListener(
  jsonMapper: JsonMapper,
  courtSentencingMigrationService: CourtSentencingMigrationService,
) : MigrationMessageListener(
  jsonMapper,
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
}

data class CourtCaseMigrationMapping(
  val offenderNo: String,
  val mapping: CourtCaseBatchMappingDto,
)
