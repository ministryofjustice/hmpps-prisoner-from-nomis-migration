package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent

@Service
class StaffSynchronisationService(
  private val nomisApiService: StaffNomisApiService,
  private val dpsApiService: StaffDpsApiService,
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun resynchroniseStaff(staffId: Long) {
    val nomisStaff = nomisApiService.getStaffDetails(staffId)
    dpsApiService.migrateStaff(nomisStaff.toMigrateStaffRequest())
  }

  suspend fun staffCreated(event: StaffEvent) {
    val telemetry = telemetryOf("nomisStaffId" to event.staffId)
    telemetryClient.trackEvent("staff-synchronisation-created-notimplemented", telemetry)
  }
  suspend fun staffUpdated(event: StaffEvent) {
    val telemetry = telemetryOf("nomisStaffId" to event.staffId)
    telemetryClient.trackEvent("staff-synchronisation-updated-notimplemented", telemetry)
  }
  suspend fun staffDeleted(event: StaffEvent) {
    val telemetry = telemetryOf("nomisStaffId" to event.staffId)
    telemetryClient.trackEvent("staff-synchronisation-deleted-notimplemented", telemetry)
  }

  suspend fun staffAccountCreated(event: StaffUserAccountEvent) {
    val telemetry = telemetryOf("nomisStaffId" to event.staffId, "username" to event.username)
    telemetryClient.trackEvent("staffuseraccounts-synchronisation-created-notimplemented", telemetry)
  }
  suspend fun staffAccountUpdated(event: StaffUserAccountEvent) {
    val telemetry = telemetryOf("nomisStaffId" to event.staffId, "username" to event.username)
    telemetryClient.trackEvent("staffuseraccounts-synchronisation-updated-notimplemented", telemetry)
  }
  suspend fun staffAccountDeleted(event: StaffUserAccountEvent) {
    val telemetry = telemetryOf("nomisStaffId" to event.staffId, "username" to event.username)
    telemetryClient.trackEvent("staffuseraccounts-synchronisation-deleted-notimplemented", telemetry)
  }

  suspend fun staffInternetAddressCreated(event: StaffInternetAddressEvent) {
    val telemetry = telemetryOf("nomisStaffId" to event.staffId, "internetAddressId" to event.internetAddressId)
    telemetryClient.trackEvent("staffinternetaddresses-synchronisation-created-notimplemented", telemetry)
  }
  suspend fun staffInternetAddressUpdated(event: StaffInternetAddressEvent) {
    val telemetry = telemetryOf("nomisStaffId" to event.staffId, "internetAddressId" to event.internetAddressId)
    telemetryClient.trackEvent("staffinternetaddresses-synchronisation-updated-notimplemented", telemetry)
  }
  suspend fun staffInternetAddressDeleted(event: StaffInternetAddressEvent) {
    val telemetry = telemetryOf("nomisStaffId" to event.staffId, "internetAddressId" to event.internetAddressId)
    telemetryClient.trackEvent("staffinternetaddresses-synchronisation-deleted-notimplemented", telemetry)
  }

  suspend fun userAccessibleCaseloadCreated(event: UserAccessibleCaseloadEvent) {
    val telemetry = telemetryOf("username" to event.username, "caseloadId" to event.caseloadId)
    telemetryClient.trackEvent("useraccessiblecaseloads-synchronisation-created-notimplemented", telemetry)
  }
  suspend fun userAccessibleCaseloadDeleted(event: UserAccessibleCaseloadEvent) {
    val telemetry = telemetryOf("username" to event.username, "caseloadId" to event.caseloadId)
    telemetryClient.trackEvent("useraccessiblecaseloads-synchronisation-deleted-notimplemented", telemetry)
  }

  suspend fun userCaseloadRoleCreated(event: UserCaseloadRoleEvent) {
    val telemetry = telemetryOf("username" to event.username, "caseloadId" to event.caseloadId, "roleCode" to event.roleCode)
    telemetryClient.trackEvent("usercaseloadroles-synchronisation-created-notimplemented", telemetry)
  }
  suspend fun userCaseloadRoleDeleted(event: UserCaseloadRoleEvent) {
    val telemetry = telemetryOf("username" to event.username, "caseloadId" to event.caseloadId, "roleCode" to event.roleCode)
    telemetryClient.trackEvent("usercaseloadroles-synchronisation-deleted-notimplemented", telemetry)
  }
}
