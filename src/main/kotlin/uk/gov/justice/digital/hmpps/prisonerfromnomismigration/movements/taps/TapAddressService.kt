package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.taps

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.movements.ExternalMovementsMappingApiService

@Service
class TapAddressService(
  override val telemetryClient: TelemetryClient,
  private val mappingApiService: ExternalMovementsMappingApiService,
  private val tapScheduleService: TapScheduleService,
) : TelemetryEnabled {

  suspend fun offenderAddressUpdated(offenderAddressUpdatedEvent: OffenderAddressUpdatedEvent) = addressDetailsUpdated(offenderAddressUpdatedEvent.addressId, "OFF")

  suspend fun corporateAddressUpdated(corporateAddressUpdatedEvent: CorporateAddressUpdatedEvent) = addressDetailsUpdated(corporateAddressUpdatedEvent.addressId, "CORP")

  suspend fun agencyAddressUpdated(agencyAddressUpdatedEvent: AgencyAddressUpdatedEvent) = addressDetailsUpdated(agencyAddressUpdatedEvent.addressId, "AGY")

  private suspend fun addressDetailsUpdated(addressId: Long, addressOwnerClass: String) {
    val addressUpdateTelemetry = mutableMapOf<String, Any>("nomisAddressId" to "$addressId", "nomisAddressOwnerClass" to addressOwnerClass)

    track("${TAP_TELEMETRY_PREFIX}-address-updated", addressUpdateTelemetry) {
      val affectedSchedules = mappingApiService.findTapScheduleMappingsForAddress(addressId)
        .also { addressUpdateTelemetry["nomisEventIds"] = it.scheduleMappings.map { it.nomisEventId }.toString() }
        .also { addressUpdateTelemetry["dpsOccurrenceIds"] = it.scheduleMappings.map { it.dpsOccurrenceId }.toString() }

      affectedSchedules.scheduleMappings.forEach { scheduleMapping ->
        val syncTelemetry = mutableMapOf<String, Any>(
          "offenderNo" to scheduleMapping.prisonerNumber,
          "bookingId" to scheduleMapping.bookingId,
          "nomisEventId" to scheduleMapping.nomisEventId,
          "directionCode" to "OUT",
        )
        track("${TAP_TELEMETRY_PREFIX}-scheduled-movement-updated", syncTelemetry) {
          tapScheduleService.syncScheduledMovementTapOut(scheduleMapping.prisonerNumber, scheduleMapping.nomisEventId, syncTelemetry, scheduleMapping, onlyIfScheduled = true)
            ?.also { tapScheduleService.tryToUpdateScheduledMovementMapping(it, syncTelemetry) }
            ?: also { syncTelemetry["ignored"] = true }
        }
      }
    }
  }
}
