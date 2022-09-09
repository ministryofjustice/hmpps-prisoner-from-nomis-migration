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
  private val mappingService: IncentiveMappingService,
  private val incentiveService: IncentivesService
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun synchroniseIncentive(iepEvent: IncentiveUpsertedOffenderEvent) {
    nomisApiService.getIncentive(iepEvent.bookingId, iepEvent.iepSeq).run {

      // todo integration and remove this log
      log.debug("received nomis incentive: $this")

      mappingService.findNomisIncentiveMapping(
        nomisBookingId = bookingId,
        nomisIncentiveSequence = incentiveSequence
      )?.let { incentiveMapping ->
        // todo remove this log
        log.debug("found nomis incentive mapping: $incentiveMapping")
        incentiveService.synchroniseUpdateIncentive(
          iepEvent.bookingId, incentiveMapping.incentiveId,
          UpdateIncentiveIEP(
            reviewTime = this.iepDateTime,
            commentText = this.commentText,
            current = this.currentIep
          )
        )
        telemetryClient.trackEvent(
          "incentive-updated-synchronisation",
          mapOf(
            "bookingId" to iepEvent.bookingId.toString(),
            "incentiveSequence" to iepEvent.iepSeq.toString(),
            "auditModuleName" to iepEvent.auditModuleName,
            "currentIep" to this.currentIep.toString()
          ),
          null
        )
        if (!this.currentIep) {
          nomisApiService.getCurrentIncentive(iepEvent.bookingId).let { currentIep ->
            log.debug("updating current IEP $this \nfollowing update to non current IEP: $currentIep")
            incentiveService.synchroniseUpdateIncentive(
              iepEvent.bookingId, incentiveMapping.incentiveId,
              UpdateIncentiveIEP(
                reviewTime = currentIep.iepDateTime,
                commentText = currentIep.commentText,
                current = currentIep.currentIep
              )
            )
            telemetryClient.trackEvent(
              "incentive-updated-synchronisation",
              mapOf(
                "bookingId" to iepEvent.bookingId.toString(),
                "incentiveSequence" to iepEvent.iepSeq.toString(),
                "auditModuleName" to iepEvent.auditModuleName,
                "currentIep" to currentIep.toString()
              ),
              null
            )
          }
        }
      } ?: run {
        log.debug("no nomis incentive mapping found")
        incentiveService.synchroniseCreateIncentive(this.toIncentive()).also {
          mappingService.createNomisIncentiveSynchronisationMapping(
            nomisBookingId = iepEvent.bookingId, nomisSequence = iepEvent.iepSeq, incentiveId = it.id
          )
          telemetryClient.trackEvent(
            "incentive-created-synchronisation",
            mapOf(
              "bookingId" to iepEvent.bookingId.toString(),
              "incentiveSequence" to iepEvent.iepSeq.toString(),
              "incentiveId" to it.id.toString(),
              "auditModuleName" to iepEvent.auditModuleName
            ),
            null
          )
        }
      }
    }
  }
}
