package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.ActivitiesMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.AllocationsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments.AppointmentsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.bedassignment.BedAssignmentByPrisonerMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonAddressContactMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonAliasIdentifierMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csra.CsraMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.advances.AdvancesMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.prisonbalances.PrisonBalanceMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.prisonerbalances.PrisonerBalanceMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.finance.scheduledpayments.ScheduledPaymentsMappingService
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
  private val bedAssignmentMappingApiService: BedAssignmentByPrisonerMappingApiService,
  private val corePersonAddressContactMappingService: CorePersonAddressContactMappingService,
  private val corePersonAliasIdentifierMappingService: CorePersonAliasIdentifierMappingService,
  private val courtSchedulerMappingService: CourtSchedulerMappingApiService,
  private val courtSentencingMappingService: CourtSentencingMappingApiService,
  private val csraMappingApiService: CsraMappingApiService,
  private val prisonBalanceMappingApiService: PrisonBalanceMappingApiService,
  private val prisonerBalanceMappingApiService: PrisonerBalanceMappingApiService,
  private val propertyMappingService: PropertyMappingService,
  private val scheduledPaymentsMappingService: ScheduledPaymentsMappingService,
  private val staffMappingApiService: StaffMappingApiService,
  private val visitMappingService: VisitMappingService,
) {
  suspend fun getMigrationCount(migrationId: String, migrationType: MigrationType): Long = when (migrationType) {
    MigrationType.ACTIVITIES -> activityMappingService.getMigrationCount(migrationId)
    MigrationType.ADVANCES -> advancesMappingService.getPagedModelMigrationCount(migrationId)
    MigrationType.AGENCY_REGISTERS -> 0
    MigrationType.ALLOCATIONS -> allocationsMappingService.getMigrationCount(migrationId)
    MigrationType.APPOINTMENTS -> appointmentsMappingService.getMigrationCount(migrationId)
    MigrationType.BED_ASSIGNMENT -> bedAssignmentMappingApiService.getMigrationCount(migrationId)
    MigrationType.CORE_PERSON_ADDRESS_CONTACT -> corePersonAddressContactMappingService.getMigrationCount(migrationId)
    MigrationType.CORE_PERSON_ALIAS_IDENTIFIER -> corePersonAliasIdentifierMappingService.getMigrationCount(migrationId)
    MigrationType.COURT_SCHEDULER -> courtSchedulerMappingService.getMigrationCount(migrationId)
    MigrationType.COURT_SENTENCING -> courtSentencingMappingService.getMigrationCount(migrationId)
    MigrationType.CSRA -> csraMappingApiService.getMigrationCount(migrationId)
    MigrationType.DRUG_TESTING -> -1 // No implementation necessary
    MigrationType.EXTERNAL_MOVEMENTS -> 0
    MigrationType.FINANCE_SCHEDULED_PAYMENTS -> scheduledPaymentsMappingService.getPagedModelMigrationCount(migrationId)
    MigrationType.PERSON_LOCATIONS -> -1 // No implementation necessary
    MigrationType.PRISON_BALANCE -> prisonBalanceMappingApiService.getPagedModelMigrationCount(migrationId)
    MigrationType.PRISONER_BALANCE -> prisonerBalanceMappingApiService.getPagedModelMigrationCount(migrationId)
    MigrationType.PROPERTY -> propertyMappingService.getMigrationCount(migrationId)
    MigrationType.STAFF -> staffMappingApiService.getPagedModelMigrationCount(migrationId)
    MigrationType.TRANSFER_MOVEMENTS -> 0 // TODO SDIT-4104 - implement
    MigrationType.VISITS -> visitMappingService.getMigrationCount(migrationId)
  }
}
