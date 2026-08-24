package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.agency

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.telemetryOf
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.track
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.helpers.trackEvent

private const val TELEMETRY_PREFIX = "agency-synchronisation"

@Service
class AgencyRegistersSynchronisationService(
  override val telemetryClient: TelemetryClient,
  private val agencyNomisApiService: AgencyNomisApiService,
  private val agencyRegistersDpsApiService: AgencyRegistersDpsApiService,
) : TelemetryEnabled {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun agencyUpdated(event: AgencyEvent) {
    val telemetry = telemetryOf(
      "agencyId" to event.agencyLocationId,
    )
    if (event.originatesInDpsOrHasMissingAudit) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-updated-skipped", telemetry)
    } else {
      track("$TELEMETRY_PREFIX-updated", telemetry) {
        val agency = agencyNomisApiService.getAgency(event.agencyLocationId)
        val legacyAgencyDto = agency.toLegacyAgencyDto()
        log.debug("updating agency for ${event.agencyLocationId} with details $legacyAgencyDto")
        agencyRegistersDpsApiService.syncAgency(event.agencyLocationId, legacyAgencyDto)
      }
    }
  }
}
