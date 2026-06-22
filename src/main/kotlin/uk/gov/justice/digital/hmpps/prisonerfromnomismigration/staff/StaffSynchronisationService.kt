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
    log.info("received a staff updated event $event")
    val telemetry = telemetryOf("nomisStaffId" to event.staffId)
    telemetryClient.trackEvent("staff-synchronisation-updated-notimplemented", telemetry)
  }

  suspend fun staffDeleted(event: StaffEvent) {
    log.info("received a staff deleted event $event")
    val telemetry = telemetryOf("nomisStaffId" to event.staffId)
    telemetryClient.trackEvent("staff-synchronisation-deleted-notimplemented", telemetry)
  }
}
