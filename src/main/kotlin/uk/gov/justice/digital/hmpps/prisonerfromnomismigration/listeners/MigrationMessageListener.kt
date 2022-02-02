package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jms.annotation.JmsListener
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.data.MigrationContext
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.MigrationMessageListener.MigrationMessage
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitsMigrationFilter
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
    val migrationMessage: MigrationMessage<*> = message.fromJson()
    when (migrationMessage.type) {
      MIGRATE_VISITS -> visitsMigrationService.migrateVisitsByPage(context(message.fromJson()))
    }
  }

  open class MigrationMessage<T>(
    val type: Messages,
    open val context: MigrationContext<T>
  )

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this, T::class.java)
}

// TODO: should be to improve this - I am having to sublcass rather then use generic, but not sure why yet ??
private fun context(message: VisitsMigrationMessage): MigrationContext<VisitsMigrationFilter> = message.context
class VisitsMigrationMessage(
  type: Messages,
  override val context: MigrationContext<VisitsMigrationFilter>
) : MigrationMessage<VisitsMigrationFilter>(type = type, context = context)
