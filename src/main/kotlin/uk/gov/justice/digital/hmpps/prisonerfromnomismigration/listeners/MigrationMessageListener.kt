package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitsMigrationService

@Service
class MigrationMessageListener(
  private val objectMapper: ObjectMapper,
  private val visitsMigrationService: VisitsMigrationService
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @JmsListener(destination = "migration", containerFactory = "hmppsQueueContainerFactoryProxy")
  fun onMessage(message: String) {
    log.debug("Received message {}", message)
    val migrationMessage: MigrationMessage = message.fromJson()
    when (migrationMessage.type) {
      MIGRATE_VISITS -> visitsMigrationService.migrateVisitsByPage()
    }
  }

  data class MigrationMessage(
    val type: Messages,
    val body: Any? = null
  )

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this, T::class.java)
}
