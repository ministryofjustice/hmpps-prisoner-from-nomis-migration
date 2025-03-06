package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.ActivitiesMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.activities.AllocationsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.appointments.AppointmentsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.casenotes.CaseNotesMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.coreperson.CorePersonMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.courtsentencing.CourtSentencingMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.csip.CSIPMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incidents.IncidentsMappingService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.organisations.OrganisationsMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.personalrelationships.ContactPersonMappingApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits.VisitMappingService

@Service
class GeneralMappingService(
  private val appointmentsMappingService: AppointmentsMappingService,
  private val visitMappingService: VisitMappingService,
  private val activityMappingService: ActivitiesMappingService,
  private val allocationsMappingService: AllocationsMappingService,
  private val corePersonMappingApiService: CorePersonMappingApiService,
  private val csipMappingService: CSIPMappingService,
  private val incidentsMappingService: IncidentsMappingService,
  private val caseNotesMappingService: CaseNotesMappingApiService,
  private val courtSentencingMappingService: CourtSentencingMappingApiService,
  private val contactPersonMappingApiService: ContactPersonMappingApiService,
  private val organisationsMappingApiService: OrganisationsMappingApiService,
) {
  suspend fun getMigrationCount(migrationId: String, migrationType: MigrationType): Long? = when (migrationType) {
    MigrationType.APPOINTMENTS -> appointmentsMappingService.getMigrationCount(migrationId)
    MigrationType.VISITS -> visitMappingService.getMigrationCount(migrationId)
    MigrationType.ACTIVITIES -> activityMappingService.getMigrationCount(migrationId)
    MigrationType.ALLOCATIONS -> allocationsMappingService.getMigrationCount(migrationId)
    MigrationType.CORE_PERSON -> corePersonMappingApiService.getMigrationCount(migrationId)
    MigrationType.CSIP -> csipMappingService.getMigrationCount(migrationId)
    MigrationType.INCIDENTS -> incidentsMappingService.getMigrationCount(migrationId)
    MigrationType.CASENOTES -> caseNotesMappingService.getMigrationCount(migrationId)
    MigrationType.COURT_SENTENCING -> courtSentencingMappingService.getMigrationCount(migrationId)
    MigrationType.PERSONALRELATIONSHIPS -> contactPersonMappingApiService.getMigrationCount(migrationId)
    MigrationType.ORGANISATIONS -> organisationsMappingApiService.getMigrationCount(migrationId)
    // since this is a patch we cannot count mappings created since none are created - it will have to be manual Telemetry
    MigrationType.SENTENCING_ADJUSTMENTS -> 0
  }
}
