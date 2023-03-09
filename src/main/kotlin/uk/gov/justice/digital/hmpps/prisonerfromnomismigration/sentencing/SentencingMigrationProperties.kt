package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.MigrationProperties
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Component
class SentencingMigrationProperties(
  hmppsQueueService: HmppsQueueService,
  sentencingAdjustmentsMappingService: SentencingAdjustmentsMappingService,
) : MigrationProperties<SentencingAdjustmentNomisMapping>(
  hmppsQueueService,
  sentencingAdjustmentsMappingService,
  MigrationType.SENTENCING_ADJUSTMENTS,
)
