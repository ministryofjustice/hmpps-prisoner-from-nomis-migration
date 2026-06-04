package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import org.springframework.stereotype.Service

@Service
class StaffSynchronisationService(
  private val nomisApiService: StaffNomisApiService,
  private val dpsApiService: StaffDpsApiService,
) {
  suspend fun resynchroniseStaff(staffId: Long) {
    val staff = nomisApiService.getStaffDetails(staffId)
    dpsApiService.migrateStaff(staff.toMigrateStaffRequest())
  }
}
