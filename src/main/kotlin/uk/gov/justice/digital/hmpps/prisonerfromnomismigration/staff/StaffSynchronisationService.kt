package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.staff

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.StaffDetails

@Service
class StaffSynchronisationService(
  private val nomisApiService: StaffNomisApiService,
  private val dpsApiService: StaffDpsApiService,
  override val telemetryClient: TelemetryClient,
) : TelemetryEnabled {

  suspend fun resynchroniseStaff(staffId: Long) {
    val nomisStaff = nomisApiService.getStaffDetailsById(staffId)
    dpsApiService.syncStaff(nomisStaff.toSyncStaffRequest())
  }

  suspend fun staffUpserted(eventType: String, event: StaffEvent) {
    val nomisStaffId = event.staffId
    val telemetry = telemetryOf("nomisStaffId" to nomisStaffId)
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
    val nomisStaffId = event.staffId
    val telemetry = telemetryOf("nomisStaffId" to nomisStaffId, "username" to event.username)
    synchroniseStaff(event, "staffuseraccount-synchronisation-$eventType", telemetry)
  }

  suspend fun staffInternetAddressUpserted(eventType: String, event: StaffInternetAddressEvent) {
    val telemetry = telemetryOf("nomisStaffId" to event.staffId, "internetAddressId" to event.internetAddressId)
    synchroniseStaff(event, "staffinternetaddress-synchronisation-$eventType", telemetry)
  }

  suspend fun userAccessibleCaseloadUpserted(eventType: String, event: UserAccessibleCaseloadEvent) {
    val telemetry = telemetryOf("username" to event.username, "caseloadId" to event.caseloadId)
    synchroniseStaffByUsername(event, "useraccessiblecaseload-synchronisation-$eventType", telemetry)
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
          dpsApiService.syncStaff(it.toSyncStaffRequest())
        }
      }
    }
  }
  private suspend fun synchroniseStaffByUsername(
    event: UserAccessibleCaseloadEvent,
    telemetryName: String,
    telemetry: MutableMap<String, Any>,
  ) {
    if (event.originatesInDpsOrHasMissingAudit) {
      telemetryClient.trackEvent("$telemetryName-skipped", telemetry)
    } else {
      nomisApiService.getStaffDetailsByUsername(event.username).also {
        track(telemetryName, telemetry) {
          dpsApiService.syncStaff(it.toSyncStaffRequest())
        }
      }
    }
  }
}

// TODO Temporary until Endpoint ready
fun StaffDetails.toSyncStaffRequest() = toMigrateStaffRequest()
