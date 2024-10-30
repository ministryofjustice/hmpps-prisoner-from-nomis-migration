package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.CSIPFullMappingDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPIdResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomissync.model.CSIPResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.CSIP_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationPage
import java.util.concurrent.CompletableFuture

@Service
class CSIPMigrationMessageListener(
  objectMapper: ObjectMapper,
  csipMigrationService: CSIPMigrationService,
) : MigrationMessageListener<CSIPMigrationFilter, CSIPIdResponse, CSIPResponse, CSIPFullMappingDto>(
  objectMapper,
  csipMigrationService,
) {

  @SqsListener(CSIP_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy", maxConcurrentMessages = "8", maxMessagesPerPoll = "8")
  fun onCSIPMessage(message: String, rawMessage: Message): CompletableFuture<Void?> {
    return onMessage(message, rawMessage)
  }

  override fun parseContextFilter(json: String): MigrationMessage<*, CSIPMigrationFilter> {
    return objectMapper.readValue(json)
  }

  override fun parseContextPageFilter(json: String): MigrationMessage<*, MigrationPage<CSIPMigrationFilter>> {
    return objectMapper.readValue(json)
  }

  override fun parseContextNomisId(json: String): MigrationMessage<*, CSIPIdResponse> {
    return objectMapper.readValue(json)
  }

  override fun parseContextMapping(json: String): MigrationMessage<*, CSIPFullMappingDto> {
    return objectMapper.readValue(json)
  }
}
