package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.MigrationProperties
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Component
class VisitMigrationProperties(
  hmppsQueueService: HmppsQueueService,
  visitMappingService: VisitMappingService,
) : MigrationProperties<VisitNomisMapping>(
  hmppsQueueService,
  visitMappingService,
  MigrationType.VISITS,
)
