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
      "agencyId" to event.agencyCode,
    )
    if (event.originatesInDpsOrHasMissingAudit) {
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-updated-skipped", telemetry)
    } else {
      val agency = agencyNomisApiService.getAgency(event.agencyCode)
      if (agency.type.code != "INST") {
        track("$TELEMETRY_PREFIX-updated", telemetry) {
          val legacyAgencyDto = agency.toLegacyAgencyDto()
          log.debug(
            "updating agency for ${event.agencyCode} with details ${
              legacyAgencyDto.copy(
                emailAddresses = legacyAgencyDto.emailAddresses.map {
                  it.copy(
                    address = "REDACTED",
                  )
                },
              )
            }",
          )
          agencyRegistersDpsApiService.syncAgency(event.agencyCode, legacyAgencyDto)
        }
      } else {
        telemetryClient.trackEvent(
          "$TELEMETRY_PREFIX-updated-ignored",
          telemetry + ("reason" to "agency is of type INST"),
        )
      }
    }
  }
}
