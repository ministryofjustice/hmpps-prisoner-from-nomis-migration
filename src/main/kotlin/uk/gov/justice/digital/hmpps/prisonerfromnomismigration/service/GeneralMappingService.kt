package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.ActivitiesMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.AllocationsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.adjudications.AdjudicationsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.alerts.AlertsByPrisonerMigrationMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments.AppointmentsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.locations.LocationsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.sentencing.SentencingAdjustmentsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitMappingService

@Service
class GeneralMappingService(
  private val appointmentsMappingService: AppointmentsMappingService,
  private val adjudicationsMigrationService: AdjudicationsMappingService,
  private val sentencingAdjustmentsMappingService: SentencingAdjustmentsMappingService,
  private val visitMappingService: VisitMappingService,
  private val activityMappingService: ActivitiesMappingService,
  private val allocationsMappingService: AllocationsMappingService,
  private val incidentsMappingService: IncidentsMappingService,
  private val csipMappingService: CSIPMappingService,
  private val locationsMappingService: LocationsMappingService,
  private val caseNotesMappingService: CaseNotesMappingApiService,
  private val alertsByPrisonerMappingService: AlertsByPrisonerMigrationMappingApiService,
) {
  suspend fun getMigrationCount(migrationId: String, migrationType: MigrationType): Long? =
    when (migrationType) {
      MigrationType.APPOINTMENTS -> appointmentsMappingService.getMigrationCount(migrationId)
      MigrationType.ADJUDICATIONS -> adjudicationsMigrationService.getMigrationCount(migrationId)
      MigrationType.SENTENCING_ADJUSTMENTS -> sentencingAdjustmentsMappingService.getMigrationCount(migrationId)
      MigrationType.VISITS -> visitMappingService.getMigrationCount(migrationId)
      MigrationType.ACTIVITIES -> activityMappingService.getMigrationCount(migrationId)
      MigrationType.ALLOCATIONS -> allocationsMappingService.getMigrationCount(migrationId)
      MigrationType.INCIDENTS -> incidentsMappingService.getMigrationCount(migrationId)
      MigrationType.CSIP -> csipMappingService.getMigrationCount(migrationId)
      MigrationType.LOCATIONS -> locationsMappingService.getMigrationCount(migrationId)
      MigrationType.CASENOTES -> caseNotesMappingService.getMigrationCount(migrationId)
      MigrationType.ALERTS -> alertsByPrisonerMappingService.getMigrationCount(migrationId)
    }
}
