package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.PrisonOffenderEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class IncentivesSynchronisationService(
  private val nomisApiService: NomisApiService,
  private val telemetryClient: TelemetryClient
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun synchroniseIncentive(iepEvent: PrisonOffenderEvent) {
    nomisApiService.getIncentive(iepEvent.bookingId, iepEvent.iepSeq).run {
      // todo integration and remove this log
      log.debug("received nomis incentive: $this")
    }
  }
}
