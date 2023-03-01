package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.MigrationProperties
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.SynchronisationType
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Component
class SentencingMigrationProperties(
  hmppsQueueService: HmppsQueueService,
  sentencingAdjustmentsMappingService: SentencingAdjustmentsMappingService,
) : MigrationProperties(
  hmppsQueueService,
  sentencingAdjustmentsMappingService,
  SynchronisationType.SENTENCING_ADJUSTMENTS
)
