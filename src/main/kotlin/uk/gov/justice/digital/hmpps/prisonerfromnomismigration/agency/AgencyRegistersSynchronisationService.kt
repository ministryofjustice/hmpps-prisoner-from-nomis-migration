package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.model.DepartmentDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agencyregisters.model.LegacyPrisonPhoneDto
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.nomisprisoner.model.AgencyResponse

private const val TELEMETRY_PREFIX = "agency-synchronisation"

@Service
class AgencyRegistersSynchronisationService(
  override val telemetryClient: TelemetryClient,
  private val agencyNomisApiService: AgencyNomisApiService,
  private val agencyRegistersDpsApiService: AgencyRegistersDpsApiService,
) : TelemetryEnabled {

  suspend fun agencyUpdated(event: AgencyEvent) {
    val telemetry = telemetryOf(
      "agencyId" to event.agencyCode,
    )
    if (event.originatesInDpsOrHasMissingAudit) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-updated-skipped", telemetry)
    } else {
      synchronise(event.agencyCode, telemetry)
    }
  }

  suspend fun resyncAgency(agencyId: String) {
    val telemetry = telemetryOf(
      "agencyId" to agencyId,
      "reason" to "repair",
    )

    synchronise(agencyId, telemetry)
  }

  suspend fun resyncPrisonPhoneNumber(agencyId: String): DepartmentDto? {
    val telemetry = telemetryOf(
      "agencyId" to agencyId,
      "reason" to "repair",
    )
    val phone = agencyNomisApiService.getAgencyPhoneNumber(agencyId).phone
    if (phone == null) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-phone-number-updated-skipped", telemetry)
      return null
    }
    return track("$TELEMETRY_PREFIX-phone-number-updated", telemetry) {
      agencyRegistersDpsApiService.updatePrisonPhoneNumber(
        agencyId,
        LegacyPrisonPhoneDto(phoneNumber = phone.toLegacyAgencyPhoneDto()),
      )
    }
  }

  private suspend fun synchronise(
    agencyId: String,
    telemetry: MutableMap<String, Any>,
  ) {
    val agency = agencyNomisApiService.getAgency(agencyId)
    track("$TELEMETRY_PREFIX-updated", telemetry) {
      val legacyAgencyDto = agency.toLegacyAgencyDto()
      if (agency.isPrison()) {
        agencyRegistersDpsApiService.syncPrison(agencyId, legacyAgencyDto)
      } else {
        agencyRegistersDpsApiService.syncAgency(agencyId, legacyAgencyDto)
      }
    }
  }
}

private fun AgencyResponse.isPrison() = type.code == "INST"
