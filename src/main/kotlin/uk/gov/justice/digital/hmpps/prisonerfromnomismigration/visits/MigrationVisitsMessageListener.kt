package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisVisit
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VISITS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.VisitId
import java.util.concurrent.CompletableFuture

@Service
class MigrationVisitsMessageListener(
  objectMapper: ObjectMapper,
  visitsMigrationService: VisitsMigrationService,
) : MigrationMessageListener<VisitsMigrationFilter, VisitId, NomisVisit, VisitNomisMapping>(
  objectMapper,
  visitsMigrationService,
) {

  @SqsListener(VISITS_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "dps-syscon-migration_visits_queue", kind = SpanKind.SERVER)
  fun onVisitMessage(message: String, rawMessage: Message): CompletableFuture<Void?> = onMessage(message, rawMessage)

  override fun parseContextFilter(json: String): MigrationMessage<*, VisitsMigrationFilter> = objectMapper.readValue(json)

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<VisitsMigrationFilter>> = objectMapper.readValue(json)

  override fun parseContextNomisId(json: String): MigrationMessage<*, VisitId> = objectMapper.readValue(json)

  override fun parseContextMapping(json: String): MigrationMessage<*, VisitNomisMapping> = objectMapper.readValue(json)
}
