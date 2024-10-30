package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CourtCaseAllMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtCaseIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.COURT_SENTENCING_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import java.util.concurrent.CompletableFuture

@Service
class CourtSentencingMigrationMessageListener(
  objectMapper: ObjectMapper,
  courtSentencingMigrationService: CourtSentencingMigrationService,
) : MigrationMessageListener<CourtSentencingMigrationFilter, CourtCaseIdResponse, CourtCaseResponse, CourtCaseAllMappingDto>(
  objectMapper,
  courtSentencingMigrationService,
) {

  @SqsListener(
    COURT_SENTENCING_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  fun onCourtSentencingMessage(message: String, rawMessage: Message): CompletableFuture<Void?> {
    return onMessage(message, rawMessage)
  }

  override fun parseContextFilter(json: String): MigrationMessage<*, CourtSentencingMigrationFilter> {
    return objectMapper.readValue(json)
  }

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<CourtSentencingMigrationFilter>> {
    return objectMapper.readValue(json)
  }

  override fun parseContextNomisId(json: String): MigrationMessage<*, CourtCaseIdResponse> {
    return objectMapper.readValue(json)
  }

  override fun parseContextMapping(json: String): MigrationMessage<*, CourtCaseAllMappingDto> {
    return objectMapper.readValue(json)
  }
}
