package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.ActivitiesMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.AllocationsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments.AppointmentsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.CsraMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.PrisonBalanceMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.PrisonerBalanceMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.OfficialVisitsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.officialvisits.VisitSlotsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitMappingService

@Service
class GeneralMappingService(
  private val appointmentsMappingService: AppointmentsMappingService,
  private val visitMappingService: VisitMappingService,
  private val activityMappingService: ActivitiesMappingService,
  private val allocationsMappingService: AllocationsMappingService,
  private val corePersonMappingApiService: CorePersonMappingApiService,
  private val csraMappingService: CsraMappingService,
  private val incidentsMappingService: IncidentsMappingService,
  private val courtSentencingMappingService: CourtSentencingMappingApiService,
  private val prisonBalanceMappingApiService: PrisonBalanceMappingApiService,
  private val prisonerBalanceMappingApiService: PrisonerBalanceMappingApiService,
  private val externalMovementsMappingApiService: ExternalMovementsMappingApiService,
  private val visitSlotsMappingService: VisitSlotsMappingService,
  private val officialVisitsMappingService: OfficialVisitsMappingService,
) {
  suspend fun getMigrationCount(migrationId: String, migrationType: MigrationType): Long = when (migrationType) {
    MigrationType.APPOINTMENTS -> appointmentsMappingService.getMigrationCount(migrationId)
    MigrationType.VISITS -> visitMappingService.getMigrationCount(migrationId)
    MigrationType.ACTIVITIES -> activityMappingService.getMigrationCount(migrationId)
    MigrationType.ALLOCATIONS -> allocationsMappingService.getMigrationCount(migrationId)
    MigrationType.CORE_PERSON -> corePersonMappingApiService.getMigrationCount(migrationId)
    MigrationType.CSRA -> csraMappingService.getMigrationCount(migrationId)
    MigrationType.INCIDENTS -> incidentsMappingService.getMigrationCount(migrationId)
    MigrationType.COURT_SENTENCING -> courtSentencingMappingService.getMigrationCount(migrationId)
    MigrationType.EXTERNAL_MOVEMENTS -> externalMovementsMappingApiService.getMigrationCount(migrationId)
    MigrationType.OFFICIAL_VISITS -> officialVisitsMappingService.getMigrationCount(migrationId)
    MigrationType.VISIT_SLOTS -> visitSlotsMappingService.getMigrationCount(migrationId)

    MigrationType.PRISON_BALANCE -> prisonBalanceMappingApiService.getPagedModelMigrationCount(migrationId)
    MigrationType.PRISONER_BALANCE -> prisonerBalanceMappingApiService.getPagedModelMigrationCount(migrationId)
  }
}
