package uk.gov.justice.digital.hmpps.prisonerfromnomismigration.visits

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisApiService
import uk.gov.justice.digital.hmpps.prisonerfromnomismigration.service.NomisVisit

@Service
class VisitSynchronisationService(
  private val nomisApiService: NomisApiService,
  private val telemetryClient: TelemetryClient,
  private val mappingService: VisitMappingService,
  private val visitService: VisitsService,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun cancelVisit(visitCancelledEvent: VisitCancelledOffenderEvent) {
    nomisApiService.getVisit(visitCancelledEvent.visitId).run {

      log.debug("received nomis visit: ${this@run}")

      if (nomisCancellation()) {
        mappingService.findNomisVisitMapping(
          visitCancelledEvent.visitId
        )?.let { visitMapping ->
          log.debug("found nomis visit mapping: $visitMapping")
          val vsipOutcome = getVsipOutcome(this@run) ?: VsipOutcome.CANCELLATION
          visitService.cancelVisit(
            visitReference = visitMapping.vsipId,
            outcome = VsipOutcomeDto(vsipOutcome, "Cancelled by NOMIS")
          )

          telemetryClient.trackEvent(
            "visit-cancellation-synchronisation",
            mapOf(
              "offenderNo" to this.offenderNo,
              "vsipId" to visitMapping.vsipId,
              "nomisVisitId" to visitMapping.nomisId.toString(),
              "vsipOutcome" to vsipOutcome.name
            ),
            null
          )
        } ?: let {
          log.debug("Ignoring visit cancellation event for ${this@run} as no nomis visit mapping found")
        }
      } else {
        log.debug("Ignoring VSIP-originated visit cancellation event for ${this@run}")
      }
    }
  }

  private fun NomisVisit.nomisCancellation() =
    this.modifyUserId != "PRISONER_MANAGER_API" // doesn't originate from VSIP
}
