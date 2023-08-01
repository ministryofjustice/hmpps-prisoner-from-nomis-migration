package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.AdjudicationsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments.AppointmentsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingAdjustmentsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitMappingService

@Service
class GeneralMappingService(
  private val appointmentsMappingService: AppointmentsMappingService,
  private val adjudicationsMigrationService: AdjudicationsMappingService,
  private val sentencingAdjustmentsMappingService: SentencingAdjustmentsMappingService,
  private val visitMappingService: VisitMappingService,
) {
  suspend fun getMigrationCount(migrationId: String, migrationType: MigrationType): Long? =
    when (migrationType) {
      MigrationType.APPOINTMENTS -> appointmentsMappingService.getMigrationCount(migrationId)
      MigrationType.ADJUDICATIONS -> adjudicationsMigrationService.getMigrationCount(migrationId)
      MigrationType.SENTENCING_ADJUSTMENTS -> sentencingAdjustmentsMappingService.getMigrationCount(migrationId)
      MigrationType.VISITS -> visitMappingService.getMigrationCount(migrationId)
    }
}
