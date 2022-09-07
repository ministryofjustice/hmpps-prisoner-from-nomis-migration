package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.incentives

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.listeners.IncentiveUpsertedOffenderEvent
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService

@Service
class IncentivesSynchronisationService(
  private val nomisApiService: NomisApiService,
  private val telemetryClient: TelemetryClient,
  private val mappingService: IncentiveMappingService
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun synchroniseIncentive(iepEvent: IncentiveUpsertedOffenderEvent) {
    nomisApiService.getIncentive(iepEvent.bookingId, iepEvent.iepSeq).run {
      // todo integration and remove this log
      log.debug("received nomis incentive: $this")

      mappingService.findNomisIncentiveMapping(iepEvent.bookingId, iepEvent.iepSeq)?.run {
        // todo remove this log
        log.debug("found nomis incentive mapping: $this")
        telemetryClient.trackEvent(
          "incentive-updated-synchronisation",
          mapOf(
            "bookingId" to iepEvent.bookingId.toString(),
            "incentiveSequence" to iepEvent.iepSeq.toString(),
            "auditModuleName" to iepEvent.auditModuleName
          ),
          null
        )
      } ?: run {
        log.debug("no nomis incentive mapping found")
        mappingService.createNomisIncentiveSynchronisationMapping(
          nomisBookingId = iepEvent.bookingId, nomisSequence = iepEvent.iepSeq, incentiveId = incentiveSequence
        )
        telemetryClient.trackEvent(
          "incentive-created-synchronisation",
          mapOf(
            "bookingId" to iepEvent.bookingId.toString(),
            "incentiveSequence" to iepEvent.iepSeq.toString(),
            "auditModuleName" to iepEvent.auditModuleName
          ),
          null
        )
      }
    }
  }
}
