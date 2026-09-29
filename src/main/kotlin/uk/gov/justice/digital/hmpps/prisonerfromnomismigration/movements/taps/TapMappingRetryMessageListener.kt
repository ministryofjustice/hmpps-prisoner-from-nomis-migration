package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.Message
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageType.RETRY_MIGRATION_MAPPING
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.asCompletableFuture
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomismappings.model.TapPrisonerMappingsDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.EXTERNAL_MOVEMENTS_QUEUE_ID
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.LocalMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationMessage
import java.util.concurrent.CompletableFuture

@Service
class TapMappingRetryMessageListener(
  private val jsonMapper: JsonMapper,
  private val repairService: TapRepairService,
) {
  private companion object {
    val log = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(
    EXTERNAL_MOVEMENTS_QUEUE_ID,
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "8",
    maxMessagesPerPoll = "8",
  )
  fun onExternalMovementMessage(message: String, rawMessage: Message): CompletableFuture<Void?> {
    log.debug("Received message {}", message)
    return asCompletableFuture {
      runCatching {
        val localMessage: LocalMessage<MigrationMessageType> = jsonMapper.readValue(message)
        check(localMessage.type == RETRY_MIGRATION_MAPPING) {
          "Only $RETRY_MIGRATION_MAPPING messages are supported for temporary absences"
        }
        val retryMessage: MigrationMessage<MigrationMessageType, TapPrisonerMappingsDto> = jsonMapper.readValue(message)
        repairService.retryCreateMapping(retryMessage.context)
      }.onFailure {
        log.error("MessageID:${rawMessage.messageId()}", it)
        throw it
      }
    }
  }
}
