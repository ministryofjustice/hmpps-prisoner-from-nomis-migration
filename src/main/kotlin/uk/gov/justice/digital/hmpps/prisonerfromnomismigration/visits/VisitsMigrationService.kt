package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.Messages.MIGRATE_VISITS
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationQueueService

@Service
class VisitsMigrationService(private val queueMigrationService: MigrationQueueService) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun migrateVisits() {
    queueMigrationService.sendMessage(MIGRATE_VISITS)
  }

  fun migrateVisitsByPage() = log.info("Will calculate visit pages to migrate")
}
