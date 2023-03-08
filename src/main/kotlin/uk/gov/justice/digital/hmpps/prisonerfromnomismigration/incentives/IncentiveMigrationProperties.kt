package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.MigrationProperties
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Component
class IncentiveMigrationProperties(
  hmppsQueueService: HmppsQueueService,
  incentiveMappingService: IncentiveMappingService,

) : MigrationProperties(
  hmppsQueueService,
  incentiveMappingService,
  MigrationType.INCENTIVES,
)
