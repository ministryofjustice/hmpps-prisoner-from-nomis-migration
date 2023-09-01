package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nonassociations

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.EventFeatureSwitch
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SQSMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.SynchronisationMessageType
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NON_ASSOCIATIONS_SYNC_QUEUE_ID
import java.util.concurrent.CompletableFuture

@Service
class NonAssociationsPrisonOffenderEventListener(
  private val nonAssociationsSynchronisationService: NonAssociationsSynchronisationService,
  private val objectMapper: ObjectMapper,
  private val eventFeatureSwitch: EventFeatureSwitch,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(NON_ASSOCIATIONS_SYNC_QUEUE_ID, factory = "hmppsQueueContainerFactoryProxy")
  @WithSpan(value = "Digital-Prison-Services-prisoner_from_nomis_nonassociations_queue", kind = SpanKind.SERVER)
  fun onMessage(message: String): CompletableFuture<Void> {
    log.debug("Received offender event message {}", message)
    val sqsMessage: SQSMessage = objectMapper.readValue(message)
    return asCompletableFuture {
      when (sqsMessage.Type) {
        "Notification" -> {
          val eventType = sqsMessage.MessageAttributes!!.eventType.Value
          if (eventFeatureSwitch.isEnabled(eventType)) {
            when (eventType) {
              "NON_ASSOCIATION_DETAIL-UPSERTED" ->
                nonAssociationsSynchronisationService.synchroniseNonAssociationCreateOrUpdate(
                  sqsMessage.Message.fromJson(),
                )

              "NON_ASSOCIATION_DETAIL-DELETED" -> log.debug(
                "NON_ASSOCIATION_DETAIL-DELETED received",
                // nonAssociationsSynchronisationService.synchroniseNonAssociationsDelete(
                (sqsMessage.Message.fromJson()),
              )

              else -> log.info("Received a message I wasn't expecting {}", eventType)
            }
          } else {
            log.info("Feature switch is disabled for event {}", eventType)
          }
        }

        SynchronisationMessageType.RETRY_NON_ASSOCIATION_MAPPING.name -> nonAssociationsSynchronisationService.retryCreateNonAssociationMapping(
          sqsMessage.Message.fromJson(),
        )
      }
    }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

data class NonAssociationsOffenderEvent(
  val offenderIdDisplay: String,
  val nsOffenderIdDisplay: String,
  val typeSeq: Int,
  val auditModuleName: String?,
)

private fun asCompletableFuture(
  process: suspend () -> Unit,
): CompletableFuture<Void> {
  return CoroutineScope(Dispatchers.Default).future {
    process()
  }.thenAccept { }
}

enum class SynchronisationMessageType {
  RETRY_SYNCHRONISATION_MAPPING,
}
