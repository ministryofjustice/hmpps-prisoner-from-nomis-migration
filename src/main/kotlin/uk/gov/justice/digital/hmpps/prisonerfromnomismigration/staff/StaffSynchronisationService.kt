package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.CaseloadResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.RoleResponse
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffAccount
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffDetails
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffEmail
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.PrisonUserSyncRequest
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.SyncPrisonUserAccount
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.SyncPrisonUserCaseload
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.SyncPrisonUserEmail
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff.model.SyncPrisonUserRole

@Service
class StaffSynchronisationService(
  private val nomisApiService: StaffNomisApiService,
  private val dpsApiService: StaffDpsApiService,
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {

  suspend fun resynchroniseStaff(staffId: Long) {
    val nomisStaff = nomisApiService.getStaffDetailsById(staffId)
    dpsApiService.syncStaff(staffId, nomisStaff.toSyncStaffRequest())
  }

  suspend fun staffUpserted(eventType: String, event: StaffEvent) {
    val telemetry = telemetryOf("nomisStaffId" to event.staffId)
    synchroniseStaff(event, "staff-synchronisation-$eventType", telemetry)
  }
  suspend fun staffDeleted(event: StaffEvent) {
    val nomisStaffId = event.staffId
    val telemetry = telemetryOf("nomisStaffId" to nomisStaffId)
    if (event.originatesInDpsOrHasMissingAudit) {
      telemetryClient.trackEvent(
        "staff-synchronisation-deleted-skipped",
        telemetry,
      )
    } else {
      dpsApiService.deleteStaff(nomisStaffId)
      telemetryClient.trackEvent("staff-synchronisation-deleted-success", telemetry)
    }
  }

  suspend fun staffAccountUpserted(eventType: String, event: StaffUserAccountEvent) {
    val telemetry = telemetryOf("nomisStaffId" to event.staffId, "username" to event.username)
    synchroniseStaff(event, "staffuseraccount-synchronisation-$eventType", telemetry)
  }

  suspend fun staffInternetAddressUpserted(eventType: String, event: StaffInternetAddressEvent) {
    val telemetry = telemetryOf("nomisStaffId" to event.staffId, "internetAddressId" to event.internetAddressId)
    synchroniseStaff(event, "staffinternetaddress-synchronisation-$eventType", telemetry)
  }

  suspend fun userAccessibleCaseloadUpserted(eventType: String, event: UserAccessibleCaseloadEvent) {
    val telemetry = telemetryOf("nomisStaffId" to event.staffId, "username" to event.username, "caseloadId" to event.caseloadId)
    synchroniseStaff(event, "useraccessiblecaseload-synchronisation-$eventType", telemetry)
  }

  suspend fun userCaseloadRoleUpserted(eventType: String, event: UserCaseloadRoleEvent) {
    val telemetry = telemetryOf("nomisStaffId" to event.staffId, "username" to event.username, "caseloadId" to event.caseloadId, "roleId" to event.roleId)
    synchroniseStaff(event, "usercaseloadrole-synchronisation-$eventType", telemetry)
  }

  private suspend fun synchroniseStaff(
    event: StaffAuditedEvent,
    telemetryName: String,
    telemetry: MutableMap<String, Any>,
  ) {
    if (event.originatesInDpsOrHasMissingAudit) {
      telemetryClient.trackEvent("$telemetryName-skipped", telemetry)
    } else {
      nomisApiService.getStaffDetailsById(event.staffId).also {
        track(telemetryName, telemetry) {
          dpsApiService.syncStaff(event.staffId, it.toSyncStaffRequest())
        }
      }
    }
  }
}

fun StaffDetails.toSyncStaffRequest() = PrisonUserSyncRequest(
  firstName = firstName,
  lastName = lastName,
  status = if (status == "ACTIVE") PrisonUserSyncRequest.Status.ACTIVE else PrisonUserSyncRequest.Status.INACTIVE,
  emails = emailAddresses.map { it.toSyncUserEmail() },
  accounts = accounts.map { it.toSyncUserAccount() },
  createdTimestamp = audit.createDatetime,
  createdBy = audit.createUsername,
  modifiedTimestamp = audit.modifyDatetime,
  modifiedBy = audit.modifyUserId,
)

private fun StaffEmail.toSyncUserEmail() = SyncPrisonUserEmail(
  // TODO check if emailAddressId needed
  // legacyEmailId = emailAddressId,
  email = email,
  createdTimestamp = audit.createDatetime,
  createdBy = audit.createUsername,
  modifiedTimestamp = audit.modifyDatetime,
  modifiedBy = audit.modifyUserId,
)

private fun StaffAccount.toSyncUserAccount() = SyncPrisonUserAccount(
  username = username,
  accountType = SyncPrisonUserAccount.AccountType.valueOf(typeCode),
  accountStatus = status.toDpsAccountStatus(),
  activeCaseloadId = activeCaseloadId,
  lastLoggedIn = lastLoggedIn,
  roles = this.caseloads.flatMap { caseload -> caseload.roles.map { it.toSyncPrisonUserRole() } },
  caseloads = caseloads.map { it.toSyncPrisonUserAccessibleCaseload() },
  createdTimestamp = audit.createDatetime,
  createdBy = audit.createUsername,
  modifiedTimestamp = audit.modifyDatetime,
  modifiedBy = audit.modifyUserId,
)

private fun String.toDpsAccountStatus() = when (this) {
  "OPEN" -> SyncPrisonUserAccount.AccountStatus.OPEN
  "EXPIRED" -> SyncPrisonUserAccount.AccountStatus.EXPIRED
  "EXPIRED & LOCKED" -> SyncPrisonUserAccount.AccountStatus.EXPIRED_LOCKED
  "EXPIRED & LOCKED(TIMED)" -> SyncPrisonUserAccount.AccountStatus.EXPIRED_LOCKED_TIMED
  "EXPIRED(GRACE)" -> SyncPrisonUserAccount.AccountStatus.EXPIRED_GRACE
  "EXPIRED(GRACE) & LOCKED" -> SyncPrisonUserAccount.AccountStatus.EXPIRED_GRACE_LOCKED
  "EXPIRED(GRACE) & LOCKED(TIMED)" -> SyncPrisonUserAccount.AccountStatus.EXPIRED_GRACE_LOCKED_TIMED
  "LOCKED" -> SyncPrisonUserAccount.AccountStatus.LOCKED
  "LOCKED(TIMED)" -> SyncPrisonUserAccount.AccountStatus.LOCKED_TIMED
  else -> throw IllegalArgumentException("Unknown Staff user account status  code: $this")
}

private fun RoleResponse.toSyncPrisonUserRole() = SyncPrisonUserRole(
  roleCode = code,
  createdTimestamp = audit.createDatetime,
  createdBy = audit.createUsername,
)

private fun CaseloadResponse.toSyncPrisonUserAccessibleCaseload() = SyncPrisonUserCaseload(
  caseloadId = caseloadId,
  createdTimestamp = audit.createDatetime,
  createdBy = audit.createUsername,
)
