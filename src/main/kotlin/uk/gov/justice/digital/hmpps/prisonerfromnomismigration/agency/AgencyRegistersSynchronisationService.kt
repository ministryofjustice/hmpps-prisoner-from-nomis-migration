package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
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
      val agency = agencyNomisApiService.getAgency(event.agencyCode)
      track("$TELEMETRY_PREFIX-updated", telemetry) {
        val legacyAgencyDto = agency.toLegacyAgencyDto()
        if (agency.isPrison()) {
          agencyRegistersDpsApiService.syncPrison(event.agencyCode, legacyAgencyDto)
        } else {
          agencyRegistersDpsApiService.syncAgency(event.agencyCode, legacyAgencyDto)
        }
      }
    }
  }
}

private fun AgencyResponse.isPrison() = type.code == "INST"
