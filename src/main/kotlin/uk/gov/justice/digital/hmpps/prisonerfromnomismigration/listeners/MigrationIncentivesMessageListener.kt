package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners

import com.amazon.sqs.javamessaging.message.SQSTextMessage
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service

@Service
class MigrationIncentivesMessageListener() {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "migrationincentives", containerFactory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String, rawMessage: SQSTextMessage) {
    log.debug("Received message {}", message)
  }
}
