package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.config.MigrationProperties
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.MigrationType
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Component
class AppointmentsMigrationProperties(
  hmppsQueueService: HmppsQueueService,
  sentencingAdjustmentsMappingService: AppointmentsMappingService,
) : MigrationProperties<AppointmentMapping>(
  hmppsQueueService,
  sentencingAdjustmentsMappingService,
  MigrationType.APPOINTMENTS,
)
