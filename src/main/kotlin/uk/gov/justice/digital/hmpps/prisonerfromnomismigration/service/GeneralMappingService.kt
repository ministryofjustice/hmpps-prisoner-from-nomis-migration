package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.ActivitiesMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.AllocationsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments.AppointmentsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.CsraMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.PrisonBalanceMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.PrisonerBalanceMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances.AdvancesMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.court.CourtSchedulerMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.property.PropertyMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.StaffMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitMappingService

@Service
class GeneralMappingService(
  private val activityMappingService: ActivitiesMappingService,
  private val advancesMappingService: AdvancesMappingService,
  private val allocationsMappingService: AllocationsMappingService,
  private val appointmentsMappingService: AppointmentsMappingService,
  private val corePersonMappingService: CorePersonMappingService,
  private val courtSchedulerMappingService: CourtSchedulerMappingApiService,
  private val courtSentencingMappingService: CourtSentencingMappingApiService,
  private val csraMappingApiService: CsraMappingApiService,
  private val prisonBalanceMappingApiService: PrisonBalanceMappingApiService,
  private val prisonerBalanceMappingApiService: PrisonerBalanceMappingApiService,
  private val propertyMappingService: PropertyMappingService,
  private val staffMappingApiService: StaffMappingApiService,
  private val visitMappingService: VisitMappingService,
) {
  suspend fun getMigrationCount(migrationId: String, migrationType: MigrationType): Long = when (migrationType) {
    MigrationType.ACTIVITIES -> activityMappingService.getMigrationCount(migrationId)
    MigrationType.ADVANCES -> advancesMappingService.getPagedModelMigrationCount(migrationId)
    MigrationType.AGENCY_REGISTERS -> 0
    MigrationType.ALLOCATIONS -> allocationsMappingService.getMigrationCount(migrationId)
    MigrationType.APPOINTMENTS -> appointmentsMappingService.getMigrationCount(migrationId)
    MigrationType.CORE_PERSON_ADDRESS_CONTACT -> corePersonMappingService.getMigrationCount(migrationId)
    MigrationType.COURT_SCHEDULER -> courtSchedulerMappingService.getMigrationCount(migrationId)
    MigrationType.COURT_SENTENCING -> courtSentencingMappingService.getMigrationCount(migrationId)
    MigrationType.CSRA -> csraMappingApiService.getMigrationCount(migrationId)
    MigrationType.DRUG_TESTING -> -1 // No implementation necessary
    MigrationType.EXTERNAL_MOVEMENTS -> 0
    MigrationType.PRISON_BALANCE -> prisonBalanceMappingApiService.getPagedModelMigrationCount(migrationId)
    MigrationType.PRISONER_BALANCE -> prisonerBalanceMappingApiService.getPagedModelMigrationCount(migrationId)
    MigrationType.PROPERTY -> propertyMappingService.getMigrationCount(migrationId)
    MigrationType.STAFF -> staffMappingApiService.getPagedModelMigrationCount(migrationId)
    MigrationType.TRANSFER_MOVEMENTS -> 0 // TODO SDIT-4104 - implement
    MigrationType.VISITS -> visitMappingService.getMigrationCount(migrationId)
  }
}
