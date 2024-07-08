package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.hmpps.kotlin.auth.HmppsReactiveAuthenticationHolder
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.time.Instant

@Service
class AuditService(
  hmppsQueueService: HmppsQueueService,
  @Value("\${spring.application.name}")
  private val serviceName: String,
  private val securityUserContext: HmppsReactiveAuthenticationHolder,
  private val mapper: ObjectMapper,
) {
  private val hmppsQueue by lazy {
    hmppsQueueService.findByQueueId("audit") ?: throw RuntimeException("Queue with name audit doesn't exist")
  }
  private val queueUrl by lazy { hmppsQueue.queueUrl }
  private val awsSqsClient by lazy {
    hmppsQueue.sqsClient
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun sendAuditEvent(what: String, details: Any) {
    val auditEvent = AuditEvent(
      what = what,
      who = securityUserContext.getPrincipal(),
      service = serviceName,
      details = mapper.writeValueAsString(details),
    )
    log.debug("Audit queue name {} {} {}", queueUrl, auditEvent, awsSqsClient)
    awsSqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(queueUrl).messageBody(mapper.writeValueAsString(auditEvent)).build(),
    )
  }
}

data class AuditEvent(
  val what: String,
  val `when`: Instant = Instant.now(),
  val who: String,
  val service: String,
  val details: String? = null,
)

enum class AuditType {
  MIGRATION_STARTED,
  MIGRATION_CANCEL_REQUESTED,
}
